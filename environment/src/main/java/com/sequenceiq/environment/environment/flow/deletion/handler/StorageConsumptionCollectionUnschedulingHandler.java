package com.sequenceiq.environment.environment.flow.deletion.handler;

import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteHandlerSelectors.UNSCHEDULE_STORAGE_CONSUMPTION_COLLECTION_EVENT;
import static com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteStateSelectors.START_RDBMS_DELETE_EVENT;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.sequenceiq.cloudbreak.auth.altus.EntitlementService;
import com.sequenceiq.environment.environment.dto.EnvironmentDeletionDto;
import com.sequenceiq.environment.environment.dto.EnvironmentDto;
import com.sequenceiq.environment.environment.flow.deletion.event.EnvDeleteEvent;
import com.sequenceiq.environment.environment.service.EnvironmentService;
import com.sequenceiq.environment.environment.service.consumption.ConsumptionService;
import com.sequenceiq.flow.reactor.api.event.EventSender;
import com.sequenceiq.flow.reactor.api.handler.EventSenderAwareHandler;

import reactor.bus.Event;

@Component
public class StorageConsumptionCollectionUnschedulingHandler extends EventSenderAwareHandler<EnvironmentDeletionDto> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageConsumptionCollectionUnschedulingHandler.class);

    private final EnvironmentService environmentService;

    private final HandlerExceptionProcessor exceptionProcessor;

    private final EntitlementService entitlementService;

    private final ConsumptionService consumptionService;

    private final boolean consumptionEnabled;

    protected StorageConsumptionCollectionUnschedulingHandler(
            EventSender eventSender,
            EnvironmentService environmentService,
            HandlerExceptionProcessor exceptionProcessor,
            EntitlementService entitlementService,
            ConsumptionService consumptionService,
            @Value("${environment.consumption.enabled:false}") boolean consumptionEnabled) {
        super(eventSender);
        this.environmentService = environmentService;
        this.exceptionProcessor = exceptionProcessor;
        this.entitlementService = entitlementService;
        this.consumptionService = consumptionService;
        this.consumptionEnabled = consumptionEnabled;
    }

    @Override
    public String selector() {
        return UNSCHEDULE_STORAGE_CONSUMPTION_COLLECTION_EVENT.selector();
    }

    @Override
    public void accept(Event<EnvironmentDeletionDto> environmentDtoEvent) {
        LOGGER.debug("Storage consumption collection unscheduling flow step started.");
        EnvironmentDeletionDto environmentDeletionDto = environmentDtoEvent.getData();
        EnvironmentDto environmentDto = environmentDeletionDto.getEnvironmentDto();
        EnvDeleteEvent nextStateEvent = getNextStateEvent(environmentDeletionDto);
        try {
            environmentService.findEnvironmentById(environmentDto.getId())
                    .ifPresent(environment -> unscheduleStorageConsumptionCollectionIfNeeded(environmentDto));
            eventSender().sendEvent(nextStateEvent, environmentDtoEvent.getHeaders());
        } catch (Exception e) {
            LOGGER.error("Storage consumption collection unscheduling failed", e);
            exceptionProcessor.handle(new HandlerFailureConjoiner(e, environmentDtoEvent, nextStateEvent), LOGGER, eventSender(), selector());
        }
    }

    private void unscheduleStorageConsumptionCollectionIfNeeded(EnvironmentDto environmentDto) {
        String accountId = environmentDto.getAccountId();
        if (consumptionEnabled && entitlementService.isCdpSaasEnabled(accountId)) {
            unscheduleStorageConsumptionCollection(environmentDto);
        } else {
            LOGGER.info("Skipping storage consumption collection unscheduling because " +
                    (consumptionEnabled ? String.format("CDP_SAAS entitlement is missing for account '%s'", accountId) : "it is disabled for the deployment"));
        }
    }

    private void unscheduleStorageConsumptionCollection(EnvironmentDto environmentDto) {
        String accountId = environmentDto.getAccountId();
        String monitoredResourceCrn = environmentDto.getResourceCrn();
        String storageLocation = environmentDto.getTelemetry().getLogging().getStorageLocation();
        LOGGER.info("Executing storage consumption collection unscheduling for account '{}', resource '{}' and storage location '{}'", accountId,
                monitoredResourceCrn, storageLocation);
        consumptionService.unscheduleStorageConsumptionCollection(accountId, monitoredResourceCrn, storageLocation);
    }

    private EnvDeleteEvent getNextStateEvent(EnvironmentDeletionDto environmentDeletionDto) {
        EnvironmentDto environmentDto = environmentDeletionDto.getEnvironmentDto();
        return EnvDeleteEvent.builder()
                .withResourceId(environmentDto.getResourceId())
                .withResourceName(environmentDto.getName())
                .withResourceCrn(environmentDto.getResourceCrn())
                .withForceDelete(environmentDeletionDto.isForceDelete())
                .withSelector(START_RDBMS_DELETE_EVENT.selector())
                .build();
    }

}
