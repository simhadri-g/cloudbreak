package com.sequenceiq.it.cloudbreak.testcase.e2e.gov;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sequenceiq.common.api.type.Tunnel;
import com.sequenceiq.it.cloudbreak.client.DistroXTestClient;
import com.sequenceiq.it.cloudbreak.client.FreeIpaTestClient;
import com.sequenceiq.it.cloudbreak.client.SdxTestClient;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.environment.EnvironmentTestDto;
import com.sequenceiq.it.cloudbreak.dto.telemetry.TelemetryTestDto;
import com.sequenceiq.it.cloudbreak.testcase.e2e.AbstractE2ETest;

public class PreconditionGovTest extends AbstractE2ETest {

    private static final Logger LOGGER = LoggerFactory.getLogger(PreconditionGovTest.class);

    @Inject
    private FreeIpaTestClient freeIpaTestClient;

    @Inject
    private SdxTestClient sdxTestClient;

    @Inject
    private DistroXTestClient distroXTestClient;

    @Override
    protected void setupTest(TestContext testContext) {
        testContext.getCloudProvider().getCloudFunctionality().cloudStorageInitialize();
        useRealUmsUser(testContext, GovUserKeys.USER_ACCOUNT_ADMIN);
        initializeDefaultBlueprints(testContext);
        useRealUmsUser(testContext, GovUserKeys.ENV_CREATOR_A);
        createDefaultCredential(testContext);
    }

    protected SdxTestClient getSdxTestClient() {
        return sdxTestClient;
    }

    protected FreeIpaTestClient getFreeIpaTestClient() {
        return freeIpaTestClient;
    }

    protected DistroXTestClient getDistroXTestClient() {
        return distroXTestClient;
    }

    @Override
    protected void createDefaultEnvironment(TestContext testContext) {
        initiateEnvironmentCreation(testContext);
        waitForEnvironmentCreation(testContext);
        waitForUserSync(testContext);
    }

    @Override
    protected void initiateEnvironmentCreation(TestContext testContext) {
        testContext
                .given("telemetry", TelemetryTestDto.class)
                    .withLogging()
                    .withReportClusterLogs()
                .given(EnvironmentTestDto.class)
                    .withNetwork()
                    .withTelemetry("telemetry")
                    .withTunnel(Tunnel.CCMV2_JUMPGATE)
                    .withCreateFreeIpa(Boolean.TRUE)
                    .withFreeIpaImage(commonCloudProperties().getImageValidation().getFreeIpaImageCatalog(),
                        commonCloudProperties().getImageValidation().getFreeIpaImageUuid())
                .when(getEnvironmentTestClient().create())
                .validate();
    }

    @Override
    protected void createDefaultDatalake(TestContext testContext) {
        initiateEnvironmentCreation(testContext);
        initiateDatalakeCreation(testContext);
        waitForEnvironmentCreation(testContext);
        waitForUserSync(testContext);
        waitForDatalakeCreation(testContext);
    }
}
