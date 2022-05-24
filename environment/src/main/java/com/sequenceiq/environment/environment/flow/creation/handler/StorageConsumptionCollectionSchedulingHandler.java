package com.sequenceiq.environment.environment.flow.creation.handler;

import static com.sequenceiq.environment.environment.flow.creation.event.EnvCreationHandlerSelectors.SCHEDULE_STORAGE_CONSUMPTION_COLLECTION_EVENT;
import static com.sequenceiq.environment.environment.flow.creation.event.EnvCreationStateSelectors.START_NETWORK_CREATION_EVENT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.auth.altus.EntitlementService;
import com.sequenceiq.consumption.api.v1.consumption.model.common.ResourceType;
import com.sequenceiq.consumption.api.v1.consumption.model.request.StorageConsumptionRequest;
import com.sequenceiq.environment.environment.dto.EnvironmentDto;
import com.sequenceiq.environment.environment.flow.creation.event.EnvCreationEvent;
import com.sequenceiq.environment.environment.flow.creation.event.EnvCreationFailureEvent;
import com.sequenceiq.environment.environment.service.EnvironmentService;
import com.sequenceiq.environment.environment.service.consumption.ConsumptionService;
import com.sequenceiq.flow.reactor.api.event.EventSender;
import com.sequenceiq.flow.reactor.api.handler.EventSenderAwareHandler;

import reactor.bus.Event;
import reactor.bus.EventBus;

@Component
public class StorageConsumptionCollectionSchedulingHandler extends EventSenderAwareHandler<EnvironmentDto> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageConsumptionCollectionSchedulingHandler.class);

    private final EnvironmentService environmentService;

    private final EventBus eventBus;

    private final EntitlementService entitlementService;

    private final ConsumptionService consumptionService;

    private final boolean consumptionEnabled;

    protected StorageConsumptionCollectionSchedulingHandler(
            EventSender eventSender,
            EnvironmentService environmentService,
            EventBus eventBus,
            EntitlementService entitlementService,
            ConsumptionService consumptionService,
            @Value("${environment.consumption.enabled:false}") boolean consumptionEnabled) {
        super(eventSender);
        this.environmentService = environmentService;
        this.eventBus = eventBus;
        this.entitlementService = entitlementService;
        this.consumptionService = consumptionService;
        this.consumptionEnabled = consumptionEnabled;
    }

    @Override
    public void accept(Event<EnvironmentDto> environmentDtoEvent) {
        LOGGER.debug("Storage consumption collection scheduling flow step started.");
        EnvironmentDto environmentDto = environmentDtoEvent.getData();
        environmentService.findEnvironmentById(environmentDto.getId())
                .ifPresentOrElse(environment -> {
                            try {
                                scheduleStorageConsumptionCollectionIfNeeded(environmentDto);
                                goToNextState(environmentDtoEvent, environmentDto);
                            } catch (Exception e) {
                                LOGGER.error("Storage consumption collection scheduling failed", e);
                                goToFailedState(environmentDtoEvent, environmentDto, e);
                            }
                        }, () -> goToFailedState(environmentDtoEvent, environmentDto,
                        new IllegalStateException(String.format("Environment was not found with id '%s'.", environmentDto.getId())))
                );
    }

    private void scheduleStorageConsumptionCollectionIfNeeded(EnvironmentDto environmentDto) {
        String accountId = environmentDto.getAccountId();
        if (consumptionEnabled && entitlementService.isCdpSaasEnabled(accountId)) {
            scheduleStorageConsumptionCollection(environmentDto);
        } else {
            LOGGER.info("Skipping storage consumption collection scheduling because " +
                    (consumptionEnabled ? String.format("CDP_SAAS entitlement is missing for account '%s'", accountId) : "it is disabled for the deployment"));
        }
    }

    private void scheduleStorageConsumptionCollection(EnvironmentDto environmentDto) {
        StorageConsumptionRequest request = new StorageConsumptionRequest();
        String resourceCrn = environmentDto.getResourceCrn();
        request.setEnvironmentCrn(resourceCrn);
        request.setMonitoredResourceCrn(resourceCrn);
        request.setMonitoredResourceName(environmentDto.getName());
        request.setMonitoredResourceType(ResourceType.ENVIRONMENT);
        request.setStorageLocation(environmentDto.getTelemetry().getLogging().getStorageLocation());
        String accountId = environmentDto.getAccountId();
        LOGGER.info("Executing storage consumption collection scheduling for account '{}' and request '{}'", accountId, request);
        consumptionService.scheduleStorageConsumptionCollection(accountId, request);
    }

    private void goToFailedState(Event<EnvironmentDto> environmentDtoEvent, EnvironmentDto environmentDto, Exception e) {
        EnvCreationFailureEvent failureEvent = new EnvCreationFailureEvent(
                environmentDto.getId(),
                environmentDto.getName(),
                e,
                environmentDto.getResourceCrn());

        eventBus.notify(failureEvent.selector(), new Event<>(environmentDtoEvent.getHeaders(), failureEvent));
    }

    private void goToNextState(Event<EnvironmentDto> environmentDtoEvent, EnvironmentDto environmentDto) {
        EnvCreationEvent nextStateEvent = EnvCreationEvent.builder()
                .withResourceId(environmentDto.getResourceId())
                .withSelector(START_NETWORK_CREATION_EVENT.selector())
                .withResourceCrn(environmentDto.getResourceCrn())
                .withResourceName(environmentDto.getName())
                .build();
        eventSender().sendEvent(nextStateEvent, environmentDtoEvent.getHeaders());
    }

    @Override
    public String selector() {
        return SCHEDULE_STORAGE_CONSUMPTION_COLLECTION_EVENT.selector();
    }

}
