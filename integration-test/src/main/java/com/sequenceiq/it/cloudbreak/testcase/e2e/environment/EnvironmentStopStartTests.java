package com.sequenceiq.it.cloudbreak.testcase.e2e.environment;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.requests.RecipeV4Type.PRE_CLOUDERA_MANAGER_START;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.sequenceiq.cloudbreak.util.SanitizerUtil;
import com.sequenceiq.distrox.api.v1.distrox.model.database.DistroXDatabaseAvailabilityType;
import com.sequenceiq.distrox.api.v1.distrox.model.database.DistroXDatabaseRequest;
import com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentStatus;
import com.sequenceiq.it.cloudbreak.CloudbreakClient;
import com.sequenceiq.it.cloudbreak.assertion.util.CloudProviderSideTagAssertion;
import com.sequenceiq.it.cloudbreak.client.CredentialTestClient;
import com.sequenceiq.it.cloudbreak.client.DistroXTestClient;
import com.sequenceiq.it.cloudbreak.client.EnvironmentTestClient;
import com.sequenceiq.it.cloudbreak.client.RecipeTestClient;
import com.sequenceiq.it.cloudbreak.client.SdxTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.RunningParameter;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.credential.CredentialTestDto;
import com.sequenceiq.it.cloudbreak.dto.distrox.DistroXTestDto;
import com.sequenceiq.it.cloudbreak.dto.environment.EnvironmentTestDto;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaTestDto;
import com.sequenceiq.it.cloudbreak.dto.recipe.RecipeTestDto;
import com.sequenceiq.it.cloudbreak.dto.sdx.SdxInternalTestDto;
import com.sequenceiq.it.cloudbreak.dto.telemetry.TelemetryTestDto;
import com.sequenceiq.it.cloudbreak.testcase.e2e.AbstractE2ETest;
import com.sequenceiq.it.cloudbreak.util.clouderamanager.ClouderaManagerUtil;
import com.sequenceiq.it.cloudbreak.util.ssh.SshJUtil;
import com.sequenceiq.it.util.ResourceUtil;
import com.sequenceiq.sdx.api.model.SdxClusterStatusResponse;

public class EnvironmentStopStartTests extends AbstractE2ETest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentStopStartTests.class);

    private static final Map<String, String> ENV_TAGS = Map.of("envTagKey", "envTagValue");

    private static final Map<String, String> SDX_TAGS = Map.of("sdxTagKey", "sdxTagValue");

    private static final Map<String, String> DX1_TAGS = Map.of("distroxTagKey", "distroxTagValue");

    private static final String MOCK_UMS_PASSWORD = "Password123!";

    private static final String CREATE_FILE_RECIPE = "classpath:/recipes/post-install.sh";

    @Inject
    private EnvironmentTestClient environmentTestClient;

    @Inject
    private SdxTestClient sdxTestClient;

    @Inject
    private DistroXTestClient distroXTestClient;

    @Inject
    private CredentialTestClient credentialTestClient;

    @Inject
    private CloudProviderSideTagAssertion cloudProviderSideTagAssertion;

    @Inject
    private ClouderaManagerUtil clouderaManagerUtil;

    @Inject
    private RecipeTestClient recipeTestClient;

    @Inject
    private SshJUtil sshJUtil;

    @Override
    protected void setupTest(TestContext testContext) {
        createDefaultUser(testContext);
        initializeDefaultBlueprints(testContext);
    }

    @Test(dataProvider = TEST_CONTEXT, timeOut = 9000000)
    @Description(
            given = "there is a running cloudbreak",
            when = "create an attached SDX and Datahubs (in case of AWS, create one of the Datahub with external database)",
            then = "should be stopped first and started after it, and required services should be in running state in CM")
    public void testCreateStopStartEnvironment(TestContext testContext) throws IOException {
        LOGGER.info("Environment stop-start test execution has been started....");
        DistroXDatabaseRequest distroXDatabaseRequest = new DistroXDatabaseRequest();
        distroXDatabaseRequest.setAvailabilityType(DistroXDatabaseAvailabilityType.NON_HA);
        String recipeName = resourcePropertyProvider().getName();
        String filePath = "/post-install";
        String fileName = "post-install";

        testContext
                .given(RecipeTestDto.class)
                    .withName(recipeName)
                    .withContent(generateRecipeContent())
                    .withRecipeType(PRE_CLOUDERA_MANAGER_START)
                .when(recipeTestClient.createV4())
                .given(CredentialTestDto.class)
                .when(credentialTestClient.create())
                .given("telemetry", TelemetryTestDto.class)
                    .withLogging()
                    .withReportClusterLogs()
                .given(EnvironmentTestDto.class)
                    .withNetwork()
                    .withTelemetry("telemetry")
                    .withCreateFreeIpa(Boolean.TRUE)
                    .withFreeIpaRecipe(Set.of(recipeName))
                    .addTags(ENV_TAGS)
                .when(environmentTestClient.create())
                .given(FreeIpaTestDto.class)
                    .withEnvironment()
                    .withTelemetry("telemetry")
                .then((tc, testDto, client) -> sshJUtil.checkFilesOnFreeIpaByNameAndPath(testDto, testDto.getCrn(), client,
                        filePath, fileName, 1, null, null))
                .given(SdxInternalTestDto.class)
                    .addTags(SDX_TAGS)
                    .withCloudStorage(getCloudStorageRequest(testContext))
                .when(sdxTestClient.createInternal())
                .given(EnvironmentTestDto.class)
                .await(EnvironmentStatus.AVAILABLE)
                .then(cloudProviderSideTagAssertion.verifyEnvironmentTags(ENV_TAGS))
                .given(SdxInternalTestDto.class)
                .await(SdxClusterStatusResponse.RUNNING)
                .then(cloudProviderSideTagAssertion.verifyInternalSdxTags(SDX_TAGS))
                .given("dx1", DistroXTestDto.class)
                    .withExternalDatabaseOnAws(distroXDatabaseRequest)
                    .addTags(DX1_TAGS)
                .when(distroXTestClient.create(), RunningParameter.key("dx1"))
                .given("dx2", DistroXTestDto.class)
                .when(distroXTestClient.create(), RunningParameter.key("dx2"))
                .given("dx1", DistroXTestDto.class)
                .await(STACK_AVAILABLE, RunningParameter.key("dx1"))
                .then(cloudProviderSideTagAssertion.verifyDistroxTags(DX1_TAGS))
                .given("dx2", DistroXTestDto.class)
                .await(STACK_AVAILABLE, RunningParameter.key("dx2"))
                .given(EnvironmentTestDto.class)
                .when(environmentTestClient.stop())
                .await(EnvironmentStatus.ENV_STOPPED)
                .given(EnvironmentTestDto.class)
                .when(environmentTestClient.start())
                .await(EnvironmentStatus.AVAILABLE)
                .given("dx1", DistroXTestDto.class)
                .await(STACK_AVAILABLE, RunningParameter.key("dx1"))
                .awaitForHealthyInstances()
                .then(this::verifyCmServicesStartedSuccessfully)
                .validate();

        LOGGER.info("Environment stop-start test execution has been finished....");
    }

    private DistroXTestDto verifyCmServicesStartedSuccessfully(TestContext testContext, DistroXTestDto testDto, CloudbreakClient cloudbreakClient) {
        String username = testContext.getActingUserCrn().getResource();
        String sanitizedUserName = SanitizerUtil.sanitizeWorkloadUsername(username);
        clouderaManagerUtil.checkCmServicesStartedSuccessfully(testDto, sanitizedUserName, MOCK_UMS_PASSWORD);
        return testDto;
    }

    private String generateRecipeContent() throws IOException {
        String recipeContentFromFile = ResourceUtil.readResourceAsString(applicationContext, CREATE_FILE_RECIPE);
        return Base64.encodeBase64String(recipeContentFromFile.getBytes());
    }
}
