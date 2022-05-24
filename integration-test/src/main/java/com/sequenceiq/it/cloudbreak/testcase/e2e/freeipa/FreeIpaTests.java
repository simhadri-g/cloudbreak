package com.sequenceiq.it.cloudbreak.testcase.e2e.freeipa;

import static com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.requests.RecipeV4Type.PRE_CLOUDERA_MANAGER_START;
import static com.sequenceiq.it.cloudbreak.context.RunningParameter.key;
import static com.sequenceiq.it.cloudbreak.context.RunningParameter.waitForFlow;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.testng.annotations.Test;

import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.instance.InstanceGroupResponse;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.instance.InstanceMetadataType;
import com.sequenceiq.freeipa.api.v1.operation.model.OperationState;
import com.sequenceiq.it.cloudbreak.FreeIpaClient;
import com.sequenceiq.it.cloudbreak.client.FreeIpaTestClient;
import com.sequenceiq.it.cloudbreak.client.RecipeTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaTestDto;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaUserSyncTestDto;
import com.sequenceiq.it.cloudbreak.dto.recipe.RecipeTestDto;
import com.sequenceiq.it.cloudbreak.dto.telemetry.TelemetryTestDto;
import com.sequenceiq.it.cloudbreak.testcase.e2e.AbstractE2ETest;
import com.sequenceiq.it.cloudbreak.util.ssh.SshJUtil;
import com.sequenceiq.it.util.ResourceUtil;

public class FreeIpaTests extends AbstractE2ETest {

    protected static final Status FREEIPA_AVAILABLE = Status.AVAILABLE;

    protected static final Status FREEIPA_DELETE_COMPLETED = Status.DELETE_COMPLETED;

    private static final String CREATE_FILE_RECIPE = "classpath:/recipes/post-install.sh";

    @Inject
    private FreeIpaTestClient freeIpaTestClient;

    @Inject
    private RecipeTestClient recipeTestClient;

    @Inject
    private SshJUtil sshJUtil;

    @Test(dataProvider = TEST_CONTEXT)
    @Description(
            given = "there is a running cloudbreak",
            when = "a valid stack create request is sent with 2 FreeIPA instances " +
                    "AND the stack is stopped " +
                    "AND the stack is started " +
                    "AND the stack is repaired one node at a time",
            then = "the stack should be available AND deletable")
    public void testCreateStopStartRepairFreeIpaWithTwoInstances(TestContext testContext) throws IOException {
        String freeIpa = resourcePropertyProvider().getName();
        String recipeName = resourcePropertyProvider().getName();
        String filePath = "/post-install";
        String fileName = "post-install";

        int instanceGroupCount = 1;
        int instanceCountByGroup = 2;

        testContext
                .given(RecipeTestDto.class)
                    .withName(recipeName)
                    .withContent(generateRecipeContent())
                    .withRecipeType(PRE_CLOUDERA_MANAGER_START)
                .when(recipeTestClient.createV4())
                .given("telemetry", TelemetryTestDto.class)
                    .withLogging()
                    .withReportClusterLogs()
                .given(freeIpa, FreeIpaTestDto.class)
                    .withFreeIpaHa(instanceGroupCount, instanceCountByGroup)
                    .withTelemetry("telemetry")
                    .withRecipe(Set.of(recipeName))
                .when(freeIpaTestClient.create(), key(freeIpa))
                .await(FREEIPA_AVAILABLE)
                .awaitForHealthyInstances()
                .then((tc, testDto, client) -> sshJUtil.checkFilesOnFreeIpaByNameAndPath(testDto, testDto.getEnvironmentCrn(), client,
                        filePath, fileName, 1, null, null))
                .when(freeIpaTestClient.stop())
                .await(Status.STOPPED)
                .when(freeIpaTestClient.start())
                .await(Status.AVAILABLE)
                .awaitForHealthyInstances()
                .when(freeIpaTestClient.repair(InstanceMetadataType.GATEWAY_PRIMARY))
                .await(Status.UPDATE_IN_PROGRESS, waitForFlow().withWaitForFlow(Boolean.FALSE))
                .await(FREEIPA_AVAILABLE)
                .awaitForHealthyInstances()
                .then((tc, testDto, client) -> sshJUtil.checkFilesOnFreeIpaByNameAndPath(testDto, testDto.getEnvironmentCrn(), client,
                        filePath, fileName, 1, null, null))
                .when(freeIpaTestClient.repair(InstanceMetadataType.GATEWAY))
                .await(Status.UPDATE_IN_PROGRESS, waitForFlow().withWaitForFlow(Boolean.FALSE))
                .await(FREEIPA_AVAILABLE)
                .awaitForHealthyInstances()
                .then((tc, testDto, client) -> sshJUtil.checkFilesOnFreeIpaByNameAndPath(testDto, testDto.getEnvironmentCrn(), client,
                        filePath, fileName, 1, null, null))
                .given(FreeIpaUserSyncTestDto.class)
                .forAllEnvironments()
                .when(freeIpaTestClient.getLastSyncOperationStatus())
                .await(OperationState.COMPLETED)
                .when(freeIpaTestClient.syncAll())
                .await(OperationState.COMPLETED)
                .given(freeIpa, FreeIpaTestDto.class)
                .then((tc, testDto, client) -> freeIpaTestClient.delete().action(tc, testDto, client))
                .await(FREEIPA_DELETE_COMPLETED)
                .validate();
    }

    private String generateRecipeContent() throws IOException {
        String recipeContentFromFile = ResourceUtil.readResourceAsString(applicationContext, CREATE_FILE_RECIPE);
        return Base64.encodeBase64String(recipeContentFromFile.getBytes());
    }

    protected <T extends FreeIpaTestDto> List<InstanceGroupResponse> getInstanceGroups(T testDto, FreeIpaClient client) {
        return client.getDefaultClient()
                .getFreeIpaV1Endpoint()
                .describe(testDto.getEnvironmentCrn())
                .getInstanceGroups();
    }
}
