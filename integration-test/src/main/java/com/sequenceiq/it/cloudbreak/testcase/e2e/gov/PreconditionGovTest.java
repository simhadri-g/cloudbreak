package com.sequenceiq.it.cloudbreak.testcase.e2e.gov;

import static java.lang.String.format;

import javax.inject.Inject;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import com.sequenceiq.common.api.type.Tunnel;
import com.sequenceiq.environment.api.v1.environment.model.request.AttachedFreeIpaRequest;
import com.sequenceiq.environment.api.v1.environment.model.request.aws.AwsFreeIpaParameters;
import com.sequenceiq.environment.api.v1.environment.model.request.aws.AwsFreeIpaSpotParameters;
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

    @Value("${integrationtest.cloudbreak.server}")
    private String defaultServer;

    @Override
    protected void setupTest(TestContext testContext) {
        testContext.getCloudProvider().getCloudFunctionality().cloudStorageInitialize();
        initUsers(testContext);
        initializeDefaultBlueprints(testContext);
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
                    .withTunnel(getTunnel())
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

    protected Tunnel getTunnel() {
        checkNonEmpty("integrationtest.cloudbreak.server", defaultServer);
        if (StringUtils.containsIgnoreCase(defaultServer, "usg-1.cdp.mow-dev")) {
            LOGGER.info(format("Tested environmet is GOV Dev at '%s'. So we are using CCM2 connection to the CDP Control Plane!", defaultServer));
            return Tunnel.CCMV2_JUMPGATE;
        } else {
            return Tunnel.CLUSTER_PROXY;
        }
    }

    protected void initUsers(TestContext testContext) {
        checkNonEmpty("integrationtest.cloudbreak.server", defaultServer);
        if (StringUtils.containsIgnoreCase(defaultServer, "usg-1.cdp.mow-dev")) {
            LOGGER.info(format("Tested environmet is GOV Dev at '%s'. So we are initializing GOV Dev UMS users!", defaultServer));
            useRealUmsUser(testContext, GovUserKeys.USER_ACCOUNT_ADMIN);
            useRealUmsUser(testContext, GovUserKeys.ENV_CREATOR_A);
        } else {
            createDefaultUser(testContext);
        }
    }

    private void checkNonEmpty(String name, String value) {
        if (StringUtils.isEmpty(value)) {
            throw new NullPointerException(format("Following variable must be set whether as environment variables or (test) application.yaml: %s",
                    name.replaceAll("\\.", "_").toUpperCase()));
        }
    }

    protected AttachedFreeIpaRequest attachedFreeIpaHARequestForTest() {
        AttachedFreeIpaRequest attachedFreeIpaRequest = new AttachedFreeIpaRequest();
        AwsFreeIpaParameters awsFreeIpaParameters = new AwsFreeIpaParameters();
        AwsFreeIpaSpotParameters awsFreeIpaSpotParameters = new AwsFreeIpaSpotParameters();

        // It won't use Spot instances for FreeIpa.
        awsFreeIpaSpotParameters.setPercentage(0);
        awsFreeIpaParameters.setSpot(awsFreeIpaSpotParameters);

        attachedFreeIpaRequest.setCreate(Boolean.TRUE);
        // FreeIpa HA with 2 instances is defined.
        attachedFreeIpaRequest.setInstanceCountByGroup(2);
        attachedFreeIpaRequest.setAws(awsFreeIpaParameters);
        return attachedFreeIpaRequest;
    }

}
