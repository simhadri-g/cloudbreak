package com.sequenceiq.freeipa.service.recipe;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.RecipeV4Endpoint;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.requests.RecipeV4Request;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.requests.RecipeV4Type;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.responses.RecipeViewV4Response;
import com.sequenceiq.cloudbreak.api.endpoint.v4.recipes.responses.RecipeViewV4Responses;
import com.sequenceiq.cloudbreak.common.model.recipe.RecipeType;
import com.sequenceiq.cloudbreak.orchestrator.model.RecipeModel;
import com.sequenceiq.cloudbreak.service.CloudbreakRuntimeException;

@ExtendWith(MockitoExtension.class)
class FreeIpaRecipeServiceTest {

    @Mock
    private RecipeV4Endpoint recipeV4Endpoint;

    @InjectMocks
    private FreeIpaRecipeService freeIpaRecipeService;

    @Test
    public void testGetResourceCrnListByResourceNameList() {
        RecipeViewV4Responses recipeViewV4Responses = new RecipeViewV4Responses();
        RecipeViewV4Response recipeResponse1 = new RecipeViewV4Response();
        recipeResponse1.setName("recipe1");
        recipeResponse1.setCrn("crn1");
        RecipeViewV4Response recipeResponse2 = new RecipeViewV4Response();
        recipeResponse2.setName("recipe2");
        recipeResponse2.setCrn("crn2");
        recipeViewV4Responses.setResponses(Set.of(recipeResponse1, recipeResponse2));
        when(recipeV4Endpoint.list(any())).thenReturn(recipeViewV4Responses);
        List<String> resourceCrnListByResourceNameList = freeIpaRecipeService.getResourceCrnListByResourceNameList(List.of("recipe1", "recipe2"));
        assertThat(resourceCrnListByResourceNameList).containsExactlyInAnyOrder("crn1", "crn2");
    }

    @Test
    public void testGetResourceCrnListByResourceNameListButOnlyOneRecipeWasFound() {
        RecipeViewV4Responses recipeViewV4Responses = new RecipeViewV4Responses();
        RecipeViewV4Response recipeResponse1 = new RecipeViewV4Response();
        recipeResponse1.setName("recipe1");
        recipeResponse1.setCrn("crn1");
        recipeViewV4Responses.setResponses(Set.of(recipeResponse1));
        when(recipeV4Endpoint.list(any())).thenReturn(recipeViewV4Responses);
        CloudbreakRuntimeException cloudbreakRuntimeException = Assertions.assertThrows(CloudbreakRuntimeException.class,
                () -> freeIpaRecipeService.getResourceCrnListByResourceNameList(List.of("recipe1", "recipe2")));
        Assertions.assertEquals("Following recipes does not exist: [recipe2]", cloudbreakRuntimeException.getMessage());
    }

    @Test
    public void testGetRecipes() {
        RecipeV4Request recipe1Request = new RecipeV4Request();
        recipe1Request.setName("recipe1");
        recipe1Request.setType(RecipeV4Type.PRE_CLOUDERA_MANAGER_START);
        recipe1Request.setContent("YmFzaDE=");
        RecipeV4Request recipe2Request = new RecipeV4Request();
        recipe2Request.setName("recipe2");
        recipe2Request.setType(RecipeV4Type.PRE_TERMINATION);
        recipe2Request.setContent("YmFzaDI=");
        when(recipeV4Endpoint.getRequest(0L, "recipe1")).thenReturn(recipe1Request);
        when(recipeV4Endpoint.getRequest(0L, "recipe2")).thenReturn(recipe2Request);
        List<RecipeModel> recipes = freeIpaRecipeService.getRecipes(Set.of("recipe1", "recipe2"));
        RecipeModel recipeModel1 = recipes.stream().filter(recipeModel -> "recipe1".equals(recipeModel.getName())).findFirst().get();
        RecipeModel recipeModel2 = recipes.stream().filter(recipeModel -> "recipe2".equals(recipeModel.getName())).findFirst().get();
        Assertions.assertEquals(RecipeType.PRE_CLOUDERA_MANAGER_START, recipeModel1.getRecipeType());
        Assertions.assertEquals(RecipeType.PRE_TERMINATION, recipeModel2.getRecipeType());
        Assertions.assertEquals("bash1", recipeModel1.getGeneratedScript());
        Assertions.assertEquals("bash2", recipeModel2.getGeneratedScript());
    }

}