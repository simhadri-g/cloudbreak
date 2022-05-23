package com.sequenceiq.environment.environment.service.recipe;

import static com.sequenceiq.authorization.resource.AuthorizationResourceType.RECIPE;

import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sequenceiq.authorization.resource.AuthorizationResourceType;
import com.sequenceiq.authorization.service.AuthorizationResourceCrnListProvider;
import com.sequenceiq.cloudbreak.api.endpoint.v4.common.CompactViewV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.RecipeV4Endpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.responses.RecipeViewV4Responses;
import com.sequenceiq.cloudbreak.service.CloudbreakRuntimeException;

@Service
public class RecipeService implements AuthorizationResourceCrnListProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(RecipeService.class);

    @Inject
    private RecipeV4Endpoint recipeV4Endpoint;

    public RecipeService(RecipeV4Endpoint recipeV4Endpoint) {
        this.recipeV4Endpoint = recipeV4Endpoint;
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
