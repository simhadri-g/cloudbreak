package com.sequenceiq.it.cloudbreak.testcase.e2e.gov;

import org.testng.annotations.Test;

import com.sequenceiq.it.cloudbreak.context.Description;
import com.sequenceiq.it.cloudbreak.context.TestContext;
import com.sequenceiq.it.cloudbreak.dto.distrox.DistroXTestDto;
import com.sequenceiq.it.cloudbreak.util.spot.UseSpotInstances;

public class BasicDistroXTests extends PreconditionGovTest {

    @Override
    protected void setupTest(TestContext testContext) {
        super.setupTest(testContext);
        createDefaultDatalake(testContext);
    }

    @Test(dataProvider = TEST_CONTEXT)
    @UseSpotInstances
    @Description(
            given = "there is an available environment with CCM2 and FreeIpa",
            when = "creating a new DistroX with default parameters",
            then = "DistroX should be created successfuly and get in RUNNING state")
    public void testCreateDistroX(TestContext testContext) {
        testContext
                .given(DistroXTestDto.class)
                .when(getDistroXTestClient().create())
                .await(STACK_AVAILABLE)
                .awaitForHealthyInstances()
                .when(getDistroXTestClient().get())
                .validate();
    }
}
