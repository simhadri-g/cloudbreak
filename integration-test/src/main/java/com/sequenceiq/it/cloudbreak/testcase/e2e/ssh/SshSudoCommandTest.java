package com.sequenceiq.it.cloudbreak.testcase.e2e.ssh;

import java.util.Set;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;

import com.sequenceiq.cloudbreak.auth.crn.RegionAwareInternalCrnGeneratorFactory;
import com.sequenceiq.freeipa.api.v1.operation.model.OperationState;
import com.sequenceiq.it.cloudbreak.client.FreeIpaTestClient;
import com.sequenceiq.it.cloudbreak.client.UmsTestClient;
import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.environment.EnvironmentTestDto;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaTestDto;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaUserSyncTestDto;
import com.sequenceiq.it.cloudbreak.dto.ums.UmsTestDto;
import com.sequenceiq.it.cloudbreak.testcase.e2e.sdx.PreconditionSdxE2ETest;
import com.sequenceiq.it.cloudbreak.util.ssh.action.SshSudoCommandActions;

public class SshSudoCommandTest extends PreconditionSdxE2ETest {

    private static final Logger LOGGER = LoggerFactory.getLogger(SshSudoCommandTest.class);

    private static final String LIST_RULES_FLAG = "-l";

    private static final String CHANGE_USER_TO_ROOT_COMMAND = "su";

    private static final String WORKLOAD_PASSWORD = "Admin@123";

    @Inject
    private FreeIpaTestClient freeIpaTestClient;

    @Inject
    private UmsTestClient umsTestClient;

    @Inject
    private RegionAwareInternalCrnGeneratorFactory regionAwareInternalCrnGeneratorFactory;

    @Inject
    private SshSudoCommandActions sshSudoCommandActions;

    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown(Object[] data) {
        TestContext testContext = (TestContext) data[0];

        try {
            testContext.given(UmsTestDto.class)
                    .assignTarget(EnvironmentTestDto.class.getSimpleName())
                    .withEnvironmentPrivilegedUser()
                    .when(umsTestClient.unAssignResourceRole(testContext.getActingUserCrn().toString(), regionAwareInternalCrnGeneratorFactory));
        } catch (Exception ex) {
            LOGGER.warn("Failed to un-assign resource role at the end of the test.", ex);
        } finally {
            testContext.cleanupTestContext();
        }
    }

    @Test(dataProvider = TEST_CONTEXT)
    @Description(
            given = "there is an up and running SDX cluster ",
            when = "the current user without EnvironmentPrivilegedUser role tries to run sudo commands on any VM ",
            then = "execution should be rejected "
    )
    public void testSudoCommandsWithoutEnvironmentPrivilegedUserResourceRole(TestContext testContext) {
        //TODO Start SDX
        testContext
                .given(UmsTestDto.class).assignTarget(EnvironmentTestDto.class.getSimpleName())
                .when(umsTestClient.setWorkloadPassword(WORKLOAD_PASSWORD, regionAwareInternalCrnGeneratorFactory))
                .given(FreeIpaTestDto.class)
                .when(freeIpaTestClient.describe())
                .given(FreeIpaUserSyncTestDto.class)
                .when(freeIpaTestClient.getLastSyncOperationStatus())
                .await(OperationState.COMPLETED)
                .then((tc, testDto, client) -> {
                    Set<String> ipAddresses = tc.get(FreeIpaTestDto.class).getResponse().getFreeIpa().getServerIp();
                    //TODO extend ipAddresses with cluster node ip addresses
                    sshSudoCommandActions.checkPermissionIsMissing(ipAddresses, getWorkloadUsername(tc), WORKLOAD_PASSWORD, LIST_RULES_FLAG);
                    return testDto;
                })
                .validate();
    }

    @Test(dataProvider = TEST_CONTEXT)
    @Description(
            given = "there is an up and running SDX cluster ",
            when = "the current user with EnvironmentPrivilegedUser role tries to run sudo commands on any VM ",
            then = "execution should be applied ",
            and = "changing the current user to root should be rejected"
    )
    public void testSudoCommandsWithEnvironmentPrivilegedUserResourceRole(TestContext testContext) {
        //TODO Start SDX
        String workloadUsername = getWorkloadUsername(testContext);
        testContext
                .given(UmsTestDto.class).assignTarget(EnvironmentTestDto.class.getSimpleName())
                .when(umsTestClient.setWorkloadPassword(WORKLOAD_PASSWORD, regionAwareInternalCrnGeneratorFactory))
                .given(UmsTestDto.class).assignTarget(EnvironmentTestDto.class.getSimpleName())
                .withEnvironmentPrivilegedUser()
                .when(umsTestClient.assignResourceRole(getEmailAddress(testContext), regionAwareInternalCrnGeneratorFactory))
                .given(FreeIpaTestDto.class)
                .when(freeIpaTestClient.describe())
                .given(FreeIpaUserSyncTestDto.class)
                .when(freeIpaTestClient.getLastSyncOperationStatus())
                .await(OperationState.COMPLETED)
                .then((tc, testDto, client) -> {
                    Set<String> ipAddresses = tc.get(FreeIpaTestDto.class).getResponse().getFreeIpa().getServerIp();
                    //TODO extend ipAddresses with cluster node ip addresses
                    sshSudoCommandActions.checkPermission(ipAddresses, workloadUsername, WORKLOAD_PASSWORD, LIST_RULES_FLAG);
                    sshSudoCommandActions.checkPermissionIsMissing(ipAddresses, workloadUsername, WORKLOAD_PASSWORD, CHANGE_USER_TO_ROOT_COMMAND);
                    return testDto;
                })
                .validate();
    }

    private String getWorkloadUsername(TestContext testContext) {
        String userCrn = testContext.getActingUserCrn().toString();
        return testContext
                .given(UmsTestDto.class)
                .assignTarget(EnvironmentTestDto.class.getSimpleName())
                .when(umsTestClient.getUserDetails(userCrn, regionAwareInternalCrnGeneratorFactory))
                .getResponse().getWorkloadUsername();
    }

    private String getEmailAddress(TestContext testContext) {
        String userCrn = testContext.getActingUserCrn().toString();
        return testContext
                .given(UmsTestDto.class)
                .assignTarget(EnvironmentTestDto.class.getSimpleName())
                .when(umsTestClient.getUserDetails(userCrn, regionAwareInternalCrnGeneratorFactory))
                .getResponse().getEmail();
    }
}
