package com.sequenceiq.freeipa.service.recipe;

import static com.sequenceiq.authorization.resource.AuthorizationResourceType.RECIPE;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.commons.codec.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.sequenceiq.authorization.resource.AuthorizationResourceType;
import com.sequenceiq.authorization.service.AuthorizationResourceCrnListProvider;
import com.sequenceiq.cloudbreak.api.endpoint.v4.common.CompactViewV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.RecipeV4Endpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.requests.RecipeV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.requests.RecipeV4Type;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.responses.RecipeViewV4Responses;
import com.sequenceiq.cloudbreak.common.model.recipe.RecipeType;
import com.sequenceiq.cloudbreak.orchestrator.model.RecipeModel;

@Service
public class FreeIpaRecipeService implements AuthorizationResourceCrnListProvider {

    private static final Logger LOGGER = LoggerFactory.getLogger(FreeIpaRecipeService.class);

    @Inject
    private RecipeV4Endpoint recipeV4Endpoint;

    @Override
    public List<String> getResourceCrnListByResourceNameList(List<String> resourceNames) {
        RecipeViewV4Responses recipes = recipeV4Endpoint.list(0L);
        List<String> recipeNamesFromCore = recipes.getResponses().stream().map(CompactViewV4Response::getName).collect(Collectors.toList());
        List<String> recipesNotFound = resourceNames.stream().filter(recipeName -> !recipeNamesFromCore.contains(recipeName)).collect(Collectors.toList());
        if (recipesNotFound.size() > 0) {
            throw new RuntimeException("Following recipes does not exist: " + recipesNotFound);
        }
        return recipes.getResponses().stream().filter(recipe -> resourceNames.contains(recipe.getName())).map(CompactViewV4Response::getCrn)
                .collect(Collectors.toList());
    }

    public List<RecipeModel> getRecipes(Set<String> recipes) {
        LOGGER.info("Get recipes from core: {}", recipes);
        return recipes.stream().map(recipe -> {
            RecipeV4Request recipeV4Request = recipeV4Endpoint.getRequest(0L, recipe);
            return new RecipeModel(recipeV4Request.getName(), recipeType(recipeV4Request.getType()),
                    new String(Base64.decodeBase64(recipeV4Request.getContent())));
        }).collect(Collectors.toList());
    }

    @Override
    public AuthorizationResourceType getSupportedAuthorizationResourceType() {
        return RECIPE;
    }

    private RecipeType recipeType(RecipeV4Type recipeType) {
        if (recipeType.equals(RecipeV4Type.POST_AMBARI_START)) {
            return RecipeType.POST_CLOUDERA_MANAGER_START;
        } else if (recipeType.equals(RecipeV4Type.PRE_AMBARI_START)) {
            return RecipeType.PRE_CLOUDERA_MANAGER_START;
        }
        return RecipeType.valueOf(recipeType.name());
    }

}
