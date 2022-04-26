package com.sequenceiq.it.cloudbreak.testcase.e2e.l0promotion;

import static com.sequenceiq.it.cloudbreak.cloud.HostGroupType.MASTER;
import static com.sequenceiq.it.cloudbreak.context.RunningParameter.expectedMessage;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.testng.annotations.Test;

import com.sequenceiq.cloudbreak.auth.crn.RegionAwareInternalCrnGeneratorFactory;
import com.sequenceiq.cloudbreak.common.mappable.CloudPlatform;
import com.sequenceiq.common.api.type.Tunnel;
import com.sequenceiq.environment.api.v1.environment.model.response.EnvironmentStatus;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.Status;
import com.sequenceiq.freeipa.api.v1.freeipa.stack.model.common.instance.InstanceGroupResponse;
import com.sequenceiq.freeipa.api.v1.operation.model.OperationState;
import com.sequenceiq.it.cloudbreak.FreeIpaClient;
import com.sequenceiq.it.cloudbreak.client.EnvironmentTestClient;
import com.sequenceiq.it.cloudbreak.client.FreeIpaTestClient;
import com.sequenceiq.it.cloudbreak.client.UmsTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.environment.EnvironmentNetworkTestDto;
import com.sequenceiq.it.cloudbreak.dto.environment.EnvironmentTestDto;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaTestDto;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaUserSyncTestDto;
import com.sequenceiq.it.cloudbreak.dto.telemetry.TelemetryTestDto;
import com.sequenceiq.it.cloudbreak.dto.ums.UmsTestDto;
import com.sequenceiq.it.cloudbreak.exception.TestFailException;
import com.sequenceiq.it.cloudbreak.testcase.e2e.AbstractE2ETest;
import com.sequenceiq.it.cloudbreak.util.clouderamanager.ClouderaManagerUtil;
import com.sequenceiq.it.cloudbreak.util.ssh.SshJUtil;

public class EnvironmentPrivilegedUserTest extends AbstractE2ETest {

    @Inject
    private FreeIpaTestClient freeIpaTestClient;

    @Inject
    private EnvironmentTestClient environmentTestClient;

    @Inject
    private UmsTestClient umsTestClient;

    @Inject
    private RegionAwareInternalCrnGeneratorFactory regionAwareInternalCrnGeneratorFactory;

    @Inject
    private SshJUtil sshJUtil;

    @Inject
    private ClouderaManagerUtil clouderaManagerUtil;

    @Override
    protected void setupTest(TestContext testContext) {
        assertSupportedCloudPlatform(CloudPlatform.AWS);
        testContext.getCloudProvider().getCloudFunctionality().cloudStorageInitialize();
        useRealUmsUser(testContext, L0UserKeys.USER_ACCOUNT_ADMIN);
        initializeDefaultBlueprints(testContext);
        useRealUmsUser(testContext, L0UserKeys.ENV_CREATOR_A);
        createDefaultCredential(testContext);
        // TODO: For the future test (with SDX and Distrox) we should create a common method!
        //  This is a temporary solution while the initial version is creating only FreeIpa and Environment
        //  for this test.
        //  Final version should contain the 'createDefaultDatahub(testContext);' here.
    }

    @Test(dataProvider = TEST_CONTEXT)
    @Description(
            given = "there is a running distrox",
            when = "assign resource roles for a secondary user on the running environment",
                and = "SSH to the MASTER node where checking the Cloudera Manager UUID file," +
                    " then checking CMAdmin group and HIVE resource config mappings via Cloudera Manager API",
            then = "SSH and Cloudera Manager access should be successful with the secondary user, 'uuid' file" +
                    " and expected mappings should be present")
    public void testFreeIpaSshWithNewWorkloadPassword(TestContext testContext) {
        useRealUmsUser(testContext, L0UserKeys.ENV_CREATOR_A);

        // TODO: This is a temporary solution for creating FreeIpa and Environment for this test!
        //  Final version should contain the 'createDefaultDatahub(testContext);' in the
        //  'setupTest()' and we can remove this Environment and FreeIpa create part from here.
        testContext
                .given(EnvironmentNetworkTestDto.class)
                .given("telemetry", TelemetryTestDto.class)
                    .withLogging()
                    .withReportClusterLogs()
                .given(EnvironmentTestDto.class)
                    .withNetwork()
                    .withResourceEncryption()
                    .withTelemetry("telemetry")
                    .withTunnel(Tunnel.CLUSTER_PROXY)
                    .withCreateFreeIpa(Boolean.FALSE)
                .when(environmentTestClient.create())
                .await(EnvironmentStatus.AVAILABLE)
                .given(FreeIpaTestDto.class)
                    .withEnvironment()
                    .withTelemetry("telemetry")
                    .withCatalog(commonCloudProperties().getImageValidation().getFreeIpaImageCatalog(),
                        commonCloudProperties().getImageValidation().getFreeIpaImageUuid())
                .when(freeIpaTestClient.create())
                .await(Status.AVAILABLE)
                .awaitForHealthyInstances()
                .given(FreeIpaUserSyncTestDto.class)
                .when(freeIpaTestClient.getLastSyncOperationStatus())
                .await(OperationState.COMPLETED)
                .given(EnvironmentTestDto.class)
                .when(environmentTestClient.describe())
                .validate();

        testContext
                .given(UmsTestDto.class)
                .assignTarget(EnvironmentTestDto.class.getSimpleName())
                .withEnvironmentAdmin()
                .when(umsTestClient.assignResourceRole(L0UserKeys.USER_ENV_CREATOR, regionAwareInternalCrnGeneratorFactory))
                .withEnvironmentPrivilegedUser()
                .when(umsTestClient.assignResourceRole(L0UserKeys.USER_ENV_CREATOR, regionAwareInternalCrnGeneratorFactory))
                .validate();

        String newWorkloadPassword = "Admin@123";
        String workloadUsernameEnvCreator = testContext.getRealUmsUserByKey(L0UserKeys.USER_ENV_CREATOR).getWorkloadUserName();
        List<String> sshHostGroups = List.of(MASTER.getName());

        useRealUmsUser(testContext, L0UserKeys.USER_ENV_CREATOR);

        testContext
                .given(UmsTestDto.class).assignTarget(EnvironmentTestDto.class.getSimpleName())
                .when(umsTestClient.setWorkloadPassword(newWorkloadPassword, regionAwareInternalCrnGeneratorFactory))
                .given(FreeIpaUserSyncTestDto.class)
                .when(freeIpaTestClient.syncAll())
                .await(OperationState.COMPLETED)
                .given(FreeIpaTestDto.class)
                .when(freeIpaTestClient.describe())
                .then((tc, testDto, client) -> {
                    Map<String, Pair<Integer, String>> results = sshJUtil.checkCloudbreakUserOnFreeIpaHost(getInstanceGroups(testDto, client), sshHostGroups,
                            "sudo systemctl restart sssd.service && sudo systemctl status sssd.service");
                    results.values().forEach(result -> Assertions.assertEquals(0, result.getLeft()));
                    tc.waitingFor(Duration.ofMinutes(2), "Waiting for SSSD to be synchronized has been interrupted");
                    return testDto;
                })
                .then((tc, testDto, client) -> sshJUtil.checkSudoPermissionOnHost(testDto, getInstanceGroups(testDto, client), sshHostGroups,
                        workloadUsernameEnvCreator, newWorkloadPassword, "ls -la"))
                .thenException((tc, testDto, client) -> sshJUtil.checkSudoPermissionOnHost(testDto, getInstanceGroups(testDto, client), sshHostGroups,
                                workloadUsernameEnvCreator, newWorkloadPassword, "su"), TestFailException.class,
                        expectedMessage("User '" + workloadUsernameEnvCreator
                                + "' is not allowed to execute '/bin/su' as root at "
                                + hostGroupsPattern(sshHostGroups)))
                .validate();

        useRealUmsUser(testContext, L0UserKeys.ENV_CREATOR_A);
    }

    private List<InstanceGroupResponse> getInstanceGroups(FreeIpaTestDto testDto, FreeIpaClient client) {
        return client.getDefaultClient()
                .getFreeIpaV1Endpoint()
                .describe(testDto.getRequest().getEnvironmentCrn()).getInstanceGroups();
    }

    public static String hostGroupsPattern(List<String> hostGroups) {
        return String.format("[\\[]%s.*[]]\\ host group[(]s[)]!", hostGroups);
    }
}
