package com.salesmanager.shop.store.api.v1.store;

import com.google.common.collect.ImmutableMap;
import com.salesmanager.core.business.services.reference.language.LanguageService;
import com.salesmanager.core.model.content.FileContentType;
import com.salesmanager.core.model.content.InputContentFile;
import com.salesmanager.core.model.merchant.MerchantStoreCriteria;
import com.salesmanager.shop.model.catalog.product.PersistableImage;
import com.salesmanager.shop.model.entity.EntityExists;
import com.salesmanager.shop.model.shop.PersistableMerchantStore;
import com.salesmanager.shop.model.shop.ReadableBrand;
import com.salesmanager.shop.model.shop.ReadableMerchantStore;
import com.salesmanager.shop.model.shop.ReadableMerchantStoreList;
import com.salesmanager.shop.store.api.exception.UnauthorizedException;
import com.salesmanager.shop.store.controller.store.facade.StoreFacade;
import com.salesmanager.shop.store.controller.user.facade.UserFacade;
import com.salesmanager.shop.utils.ServiceRequestCriteriaBuilderUtils;
import io.swagger.annotations.ApiOperation;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1", produces = MediaType.APPLICATION_JSON_VALUE, consumes = MediaType.APPLICATION_JSON_VALUE)
public class MerchantStoreApi {

  private static final Logger LOGGER = LoggerFactory.getLogger(MerchantStoreApi.class);

  private static final Map<String, String> MAPPING_FIELDS = ImmutableMap.<String, String>builder()
      .put("name", "storename")
      .put("readableAudit.user", "auditSection.modifiedBy")
      .build();

  @Inject
  private StoreFacade storeFacade;

  @Inject
  private LanguageService languageService;

  @Inject
  private UserFacade userFacade;

  @GetMapping(value = {"/store/{store}"})
  @ApiOperation(
      httpMethod = "GET",
      value = "Get merchant store",
      notes = "",
      response = ReadableMerchantStore.class)
  public ReadableMerchantStore store(
      @PathVariable String store, @RequestParam(value = "lang", required = false) String lang) {
    return storeFacade.getByCode(store, lang);
  }

  @PostMapping(value = {"/private/store"})
  @ApiOperation(
      httpMethod = "POST",
      value = "Creates a new store",
      notes = "",
      response = ReadableMerchantStore.class)
  public ReadableMerchantStore create(@Valid @RequestBody PersistableMerchantStore store) {
    return storeFacade.create(store);
  }

  @PutMapping(value = {"/private/store/{code}"})
  @ApiOperation(
      httpMethod = "PUT",
      value = "Updates a store",
      notes = "",
      response = ReadableMerchantStore.class)
  public ReadableMerchantStore update(
      @Valid @RequestBody PersistableMerchantStore store,
      HttpServletRequest request) {

    String userName = getUserFromRequest(request);
    validateUserPermission(userName, store.getCode());
    return storeFacade.update(store);
  }

  private String getUserFromRequest(HttpServletRequest request) {
    // user doing action must be attached to the store being modified
    Principal principal = request.getUserPrincipal();
    return principal.getName();
  }

  private void validateUserPermission(String userName, String code) {
    //TODO reviewed Spring Security should be used
    if (!userFacade.authorizedStore(userName, code)) {
      throw new UnauthorizedException("User " + userName + " not authorized");
    }
  }

  @GetMapping(value = {"/private/store/{code}/marketing"})
  @ApiOperation(
      httpMethod = "GET",
      value = "Get store branding and marketing details",
      notes = "",
      response = ReadableBrand.class)
  public ReadableBrand getStoreMarketing(@PathVariable String code, HttpServletRequest request) {
    String userName = getUserFromRequest(request);
    validateUserPermission(userName, code);
    return storeFacade.getBrand(code);
  }


  @ResponseStatus(HttpStatus.CREATED)
  @PostMapping( value={"/private/store/{code}/marketing/logo"})
  public void createLogo(@PathVariable String code, @Valid @RequestBody PersistableImage image,
      HttpServletRequest request) {

      // user doing action must be attached to the store being modified
      String userName = getUserFromRequest(request);
      validateUserPermission(userName, code);

      InputContentFile cmsContentImage = createInputContentFile(image);
      storeFacade.addStoreLogo(code, cmsContentImage);
  }

  private InputContentFile createInputContentFile(@Valid PersistableImage image) {
    InputStream input = new ByteArrayInputStream(image.getBytes());

    InputContentFile cmsContentImage = new InputContentFile();
    cmsContentImage.setFileName(image.getName());
    cmsContentImage.setMimeType(image.getContentType());
    cmsContentImage.setFileContentType(FileContentType.LOGO);
    cmsContentImage.setFile(input);
    return cmsContentImage;
  }

  @DeleteMapping(value = {"/private/store/{code}/marketing/logo"})
  @ApiOperation(
      httpMethod = "DELETE",
      value = "Delete store logo",
      notes = "",
      response = Void.class)
  public void deleteStoreLogo(
      @PathVariable String code, HttpServletRequest request) {

    // user doing action must be attached to the store being modified
    String userName = getUserFromRequest(request);
    validateUserPermission(userName, code);

    // delete store logo
    storeFacade.deleteLogo(code);
  }

  @GetMapping(
      value = {"/private/store/unique"},
      produces = MediaType.APPLICATION_JSON_VALUE)
  @ApiOperation(
      httpMethod = "GET",
      value = "Check if store code already exists",
      notes = "",
      response = EntityExists.class)
  public EntityExists exists(@RequestParam(value = "code") String code) {
    // TODO should be reviewed
    boolean isStoreExist = storeFacade.existByCode(code);
    return new EntityExists(isStoreExist);
  }

  @GetMapping(value = {"/private/stores"})
  @ApiOperation(
      httpMethod = "GET",
      value = "Check list of stores",
      notes = "",
      response = ReadableMerchantStoreList.class)
  public ReadableMerchantStoreList list(
      @RequestParam(value = "start", required = false) Integer start,
      @RequestParam(value = "length", required = false) Integer count,
      @RequestParam(value = "code", required = false) String code,
      HttpServletRequest request) {

    MerchantStoreCriteria criteria = createMerchantStoreCriteria(start, count, request);
    String drawParam = request.getParameter("draw");

    return storeFacade.getByCriteria(criteria, drawParam, languageService.defaultLanguage());
  }

  private MerchantStoreCriteria createMerchantStoreCriteria(Integer start, Integer count,
      HttpServletRequest request) {
    MerchantStoreCriteria criteria = (MerchantStoreCriteria) ServiceRequestCriteriaBuilderUtils
        .buildRequest(MAPPING_FIELDS, request);

    Optional.ofNullable(start).ifPresent(criteria::setStartIndex);
    Optional.ofNullable(count).ifPresent(criteria::setMaxCount);

    String search = criteria.getSearch();
    if (!StringUtils.isBlank(search)) {
      criteria.setCode(search);
      criteria.setName(search);
    }
    return criteria;
  }

  @DeleteMapping(value = {"/private/store/{code}"})
  @ApiOperation(
      httpMethod = "DELETE",
      value = "Deletes a store",
      notes = "",
      response = ResponseEntity.class)
  public void delete(@PathVariable String code, HttpServletRequest request) {
    String userName = getUserFromRequest(request);
    validateUserPermission(userName, code);
    storeFacade.delete(code);
  }
}
