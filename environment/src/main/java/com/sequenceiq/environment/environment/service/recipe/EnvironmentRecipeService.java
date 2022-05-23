package com.sequenceiq.environment.environment.service.recipe;

import static com.sequenceiq.authorization.resource.AuthorizationResourceType.RECIPE;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sequenceiq.authorization.resource.AuthorizationResourceType;
import com.sequenceiq.authorization.service.AuthorizationResourceCrnListProvider;
import com.sequenceiq.cloudbreak.api.endpoint.v4.common.CompactViewV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.RecipeV4Endpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.responses.RecipeViewV4Responses;
import com.sequenceiq.cloudbreak.auth.altus.EntitlementService;
import com.sequenceiq.cloudbreak.service.CloudbreakRuntimeException;
import com.sequenceiq.environment.environment.dto.EnvironmentCreationDto;

@Service
public class EnvironmentRecipeService implements AuthorizationResourceCrnListProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(EnvironmentRecipeService.class);

    @Inject
    private EntitlementService entitlementService;

    @Inject
    private RecipeV4Endpoint recipeV4Endpoint;

    public EnvironmentRecipeService(EntitlementService entitlementService, RecipeV4Endpoint recipeV4Endpoint) {
        this.entitlementService = entitlementService;
        this.recipeV4Endpoint = recipeV4Endpoint;
    }

    public void validateFMSRecipesEntitlement(EnvironmentCreationDto creationDto) {
        if (!entitlementService.isFmsRecipesEnabled(creationDto.getAccountId())
                && creationDto.getFreeIpaCreation().getRecipes() != null
                && creationDto.getFreeIpaCreation().getRecipes().size() > 0) {
            throw new BadRequestException("FreeIpa recipe support is not enabled for this account");
        }
    }

    @Override
    public List<String> getResourceCrnListByResourceNameList(List<String> resourceNames) {
        LOGGER.info("Get resources crn list for recipes: {}", resourceNames);
        RecipeViewV4Responses recipes = recipeV4Endpoint.list(0L);
        List<String> recipeNamesFromCore = recipes.getResponses().stream().map(CompactViewV4Response::getName).collect(Collectors.toList());
        List<String> recipesNotFound = resourceNames.stream().filter(recipeName -> !recipeNamesFromCore.contains(recipeName)).collect(Collectors.toList());
        if (recipesNotFound.size() > 0) {
            LOGGER.info("Missing recipes: {}", recipesNotFound);
            throw new CloudbreakRuntimeException("Following recipes does not exist: " + recipesNotFound);
        }
        List<String> resourceCrns = recipes.getResponses().stream().filter(recipe -> resourceNames.contains(recipe.getName())).map(CompactViewV4Response::getCrn)
                .collect(Collectors.toList());
        LOGGER.info("Resource crns for recipes: {}", resourceCrns);
        return resourceCrns;
    }

    @Override
    public AuthorizationResourceType getSupportedAuthorizationResourceType() {
        return RECIPE;
    }

}
