package com.sequenceiq.it.cloudbreak.testcase.authorization;

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
import com.sequenceiq.it.cloudbreak.context.MockedTestContext;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.environment.EnvironmentTestDto;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaTestDto;
import com.sequenceiq.it.cloudbreak.dto.freeipa.FreeIpaUserSyncTestDto;
import com.sequenceiq.it.cloudbreak.dto.sdx.SdxInternalTestDto;
import com.sequenceiq.it.cloudbreak.dto.ums.UmsTestDto;
import com.sequenceiq.it.cloudbreak.testcase.mock.AbstractMockTest;
import com.sequenceiq.it.cloudbreak.util.ssh.action.SshSudoCommandActions;

public class EnvironmentPrivilegedUserTest extends AbstractMockTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentPrivilegedUserTest.class);

    @Inject
    private FreeIpaTestClient freeIpaTestClient;

    @Inject
    private UmsTestClient umsTestClient;

    @Inject
    private SshSudoCommandActions sshSudoCommandActions;

    @Inject
    private RegionAwareInternalCrnGeneratorFactory regionAwareInternalCrnGeneratorFactory;

    @Override
    protected void setupTest(TestContext testContext) {
        useRealUmsUser(testContext, AuthUserKeys.ACCOUNT_ADMIN);
        initializeDefaultBlueprints(testContext);
        createDefaultImageCatalog(testContext);
        useRealUmsUser(testContext, AuthUserKeys.ENV_CREATOR_A);
        createDefaultCredential(testContext);
        createEnvironmentWithFreeIpa(testContext);
    }

    @AfterMethod(alwaysRun = true)
    @Override
    public void tearDown(Object[] data) {
        MockedTestContext testContext = (MockedTestContext) data[0];

        useRealUmsUser(testContext, AuthUserKeys.ENV_CREATOR_A);

        try {
            testContext.given(UmsTestDto.class)
                    .assignTarget(EnvironmentTestDto.class.getSimpleName())
                    .withEnvironmentPrivilegedUser()
                    .when(umsTestClient.unAssignResourceRole(AuthUserKeys.ENV_ADMIN_A, regionAwareInternalCrnGeneratorFactory));
        } catch (Exception ex) {
            LOGGER.warn("Failed to un-assign resource role at the end of the test.", ex);
        } finally {
            testContext.cleanupTestContext();
        }
    }

    @Test(dataProvider = TEST_CONTEXT_WITH_MOCK)
    @Description(
            given = "there is a running environment",
            when = "the current user with EnvironmentPrivilegedUser role tries to run sudo commands on any VM ",
            then = "execution should be applied "
    )
    public void testFreeIpaSshWithNewWorkloadPassword(MockedTestContext testContext) {
        useRealUmsUser(testContext, AuthUserKeys.ENV_CREATOR_A);

        testContext
                .given(UmsTestDto.class)
                .assignTarget(EnvironmentTestDto.class.getSimpleName())
                .withEnvironmentPrivilegedUser()
                .when(umsTestClient.assignResourceRole(AuthUserKeys.ENV_ADMIN_A, regionAwareInternalCrnGeneratorFactory))
                .validate();

        useRealUmsUser(testContext, AuthUserKeys.ENV_ADMIN_A);

        String listRulesFlag = "-l";
        String rootCommand = "su";
        String newWorkloadPassword = "Admin@123";
        String workloadUsername = testContext.getActingUser().getWorkloadUserName();

        testContext
                .given(FreeIpaUserSyncTestDto.class)
                .when(freeIpaTestClient.syncAll())
                .await(OperationState.COMPLETED)
                .given(UmsTestDto.class).assignTarget(EnvironmentTestDto.class.getSimpleName())
                .when(umsTestClient.setWorkloadPassword(newWorkloadPassword, regionAwareInternalCrnGeneratorFactory))
                .given(FreeIpaUserSyncTestDto.class)
                .when(freeIpaTestClient.syncAll())
                .await(OperationState.COMPLETED)
                .given(SdxInternalTestDto.class)
                .then((tc, testDto, client) -> {
                    Set<String> ipAddresses = tc.get(FreeIpaTestDto.class).getResponse().getFreeIpa().getServerIp();
                    sshSudoCommandActions.checkPermission(ipAddresses, workloadUsername, newWorkloadPassword, listRulesFlag);
                    sshSudoCommandActions.checkPermissionIsMissing(ipAddresses, workloadUsername, newWorkloadPassword, rootCommand);
                    return testDto;})
                .validate();
    }
}
