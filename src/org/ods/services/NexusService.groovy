package org.ods.services

@Grab(group='com.konghq', module='unirest-java', version='2.4.03', classifier='standalone')

import com.cloudbees.groovy.cps.NonCPS
import kong.unirest.Unirest
import org.apache.commons.io.FilenameUtils
import org.apache.http.client.utils.URIBuilder
import org.ods.util.IPipelineSteps
import org.ods.util.PipelineSteps

class NexusService {

    final URI baseURL

    final String username
    final String password

    final IPipelineSteps steps

    NexusService(IPipelineSteps steps, String baseURL, String username, String password) {
        if (steps == null) {
            throw new NullPointerException("Error: unable to connect to Nexus. An instance of IPipelineSteps is required.")
        }

        if (!baseURL?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'baseURL' is undefined.")
        }

        if (!username?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'username' is undefined.")
        }

        if (!password?.trim()) {
            throw new IllegalArgumentException("Error: unable to connect to Nexus. 'password' is undefined.")
        }

        this.steps = steps

        try {
            this.baseURL = new URIBuilder(baseURL).build()
        } catch (e) {
            throw new IllegalArgumentException(
                "Error: unable to connect to Nexus. '${baseURL}' is not a valid URI."
            ).initCause(e)
        }

        this.username = username
        this.password = password
    }

    @SuppressWarnings('SpaceAroundMapEntryColon')
    @NonCPS
    URI storeArtifact(String repository, String directory, String name, byte[] artifact, String contentType) {
        Map nexusParams = [
            'raw.directory':directory,
            'raw.asset1.filename':name,
        ]

        return storeComplextArtifact(repository, artifact, contentType, 'raw', nexusParams)
    }

    URI storeArtifactFromFile(
        String repository,
        String directory,
        String name,
        String artifact,
        String contentType) {
        this.steps.dir(FilenameUtils.getFullPath(artifact)) {
            def base64 = steps.readFile(FilenameUtils.getName(artifact), 'Base64')
            return storeArtifact(repository, directory, name, base64.decodeBase64(), contentType)
        }
    }

    @SuppressWarnings('LineLength')
    @NonCPS
    URI storeComplextArtifact(String repository, byte[] artifact, String contentType, String repositoryType, Map nexusParams = [ : ]) {
        def restCall = Unirest.post("${this.baseURL}/service/rest/v1/components?repository={repository}")
            .routeParam('repository', repository)
            .basicAuth(this.username, this.password)

        nexusParams.each { key, value ->
            restCall = restCall.field(key, value)
        }

        restCall = restCall.field(
            repositoryType == 'raw' || repositoryType == 'maven2' ? "${repositoryType}.asset1" : "${repositoryType}.asset",
            new ByteArrayInputStream(artifact), contentType)

        def response = restCall.asString()
        response.ifSuccess {
            if (response.getStatus() != 204) {
                throw new RuntimeException(
                    'Error: unable to store artifact. ' +
                        "Nexus responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."
                )
            }
        }

        response.ifFailure {
            def message = 'Error: unable to store artifact. ' +
                "Nexus responded with code: '${response.getStatus()}' and message: '${response.getBody()}'."

            if (response.getStatus() == 404) {
                message = "Error: unable to store artifact. Nexus could not be found at: '${this.baseURL}'."
            }

            throw new RuntimeException(message)
        }

        if (repositoryType == 'raw') {
            return this.baseURL.resolve("/repository/${repository}/${nexusParams['raw.directory']}/" +
                "${nexusParams['raw.asset1.filename']}")
        }
        return this.baseURL.resolve("/repository/${repository}")
    }

    @SuppressWarnings(['LineLength', 'JavaIoPackageAccess'])
    @NonCPS
    Map<String, Object> retrieveArtifact(String nexuseRepository, String nexusDirectory, String name, String extractionPath) {
        // https://nexus3-cd....../repository/leva-documentation/odsst-WIP/DTP-odsst-WIP-108.zip
        String urlToDownload = "${this.baseURL}/repository/${nexuseRepository}/${nexusDirectory}/${name}"
        def restCall = Unirest.get("${urlToDownload}")
            .basicAuth(this.username, this.password)
        def response = restCall.asBytes()

        response.ifFailure {
            def message = 'Error: unable to get artifact. ' +
                "Nexus responded with code: '${response.getStatus()}' and message: '${response.getBody()}'." +
                " The url called was: ${urlToDownload}"

            if (response.getStatus() == 404) {
                message = "Error: unable to get artifact. Nexus could not be found at: '${urlToDownload}'."
            }
            // very weird, we get a 200 as failure with a good artifact, wtf.
            if (response.getStatus() != 200) {
                throw new RuntimeException(message)
            }
        }

        this.steps.dir(extractionPath) {
            def base64 = response.body.encodeBase64().toString()
            steps.writeFile(name, base64, 'Base64')
        }

        return [
            uri: this.baseURL.resolve("/repository/${nexuseRepository}/${nexusDirectory}/${name}"),
            content: response.getBody(),
        ]
    }

}
