<#--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
-->

<#--
    findProduct.ftl - Product Search Screen
    Step 8: FreeMarker Template for searching products using the findProduct service.
    Includes input fields for Product ID, Product Name, Category, Price Range, and Features.
    Displays results in a tabular format with pagination support.
-->

<div class="screenlet">
    <div class="screenlet-title-bar">
        <ul>
            <li class="h3">Find Product</li>
            <li><a href="<@ofbizUrl>EditProduct</@ofbizUrl>">Create New Product</a></li>
        </ul>
        <br class="clear"/>
    </div>
    <div class="screenlet-body">
        <form id="findProductForm" method="post" action="<@ofbizUrl>FindProduct</@ofbizUrl>">
            <table class="basic-table" cellspacing="0">
                <tr>
                    <td class="label">Product ID</td>
                    <td><input type="text" id="productId" name="productId" value="${parameters.productId!}" size="20"/></td>
                    <td class="label">Product Name</td>
                    <td><input type="text" id="productName" name="productName" value="${parameters.productName!}" size="20"/></td>
                </tr>
                <tr>
                    <td class="label">Category</td>
                    <td>
                        <#assign categories = delegator.findList("ProductCategory", null, null, null, null, false)>
                        <select name="productCategoryId" id="productCategoryId">
                            <option value="">Any Category</option>
                            <#list categories as cat>
                                <option value="${cat.productCategoryId}" <#if (parameters.productCategoryId!) == cat.productCategoryId>selected</#if>>${cat.categoryName!cat.productCategoryId}</option>
                            </#list>
                        </select>
                    </td>
                    <td class="label">Feature ID</td>
                    <td><input type="text" id="productFeatureId" name="productFeatureId" value="${parameters.productFeatureId!}" size="20"/></td>
                </tr>
                <tr>
                    <td class="label">Min Price</td>
                    <td><input type="text" id="minPrice" name="minPrice" value="${parameters.minPrice!}" size="10"/></td>
                    <td class="label">Max Price</td>
                    <td><input type="text" id="maxPrice" name="maxPrice" value="${parameters.maxPrice!}" size="10"/></td>
                </tr>
                <tr>
                    <td class="label">Feature Type</td>
                    <td>
                        <#assign featureTypes = delegator.findList("ProductFeatureType", null, null, null, null, false)>
                        <select name="productFeatureTypeId" id="productFeatureTypeId">
                            <option value="">Any Feature Type</option>
                            <#list featureTypes as ft>
                                <option value="${ft.productFeatureTypeId}" <#if (parameters.productFeatureTypeId!) == ft.productFeatureTypeId>selected</#if>>${ft.description!ft.productFeatureTypeId}</option>
                            </#list>
                        </select>
                    </td>
                    <td colspan="2"></td>
                </tr>
                <tr>
                    <td></td>
                    <td colspan="3">
                        <input type="hidden" name="noConditionFind" value="Y"/>
                        <input type="submit" value="Search" class="smallSubmit"/>
                        <a href="<@ofbizUrl>FindProduct</@ofbizUrl>" class="buttontext">Clear</a>
                    </td>
                </tr>
            </table>
        </form>
    </div>
</div>

<#-- Execute search if any parameter is provided -->
<#assign hasSearchParams = parameters.noConditionFind?has_content || parameters.productId?has_content || parameters.productName?has_content ||
    parameters.productCategoryId?has_content || parameters.minPrice?has_content ||
    parameters.maxPrice?has_content || parameters.productFeatureId?has_content ||
    parameters.productFeatureTypeId?has_content />

<#assign searchPerformed = (requestAttributes._ERROR_MESSAGE_??) == false />

<#if hasSearchParams>
    <#assign productList = [] />
    
    <#assign serviceCtx = {} />
    <#if parameters.productId?has_content>
        <#assign serviceCtx = serviceCtx + {"productId": parameters.productId} />
    </#if>
    <#if parameters.productName?has_content>
        <#assign serviceCtx = serviceCtx + {"productName": parameters.productName} />
    </#if>
    <#if parameters.productCategoryId?has_content>
        <#assign serviceCtx = serviceCtx + {"productCategoryId": parameters.productCategoryId} />
    </#if>
    <#if parameters.minPrice?has_content>
        <#assign serviceCtx = serviceCtx + {"minPrice": Static["java.math.BigDecimal"].new(parameters.minPrice)} />
    </#if>
    <#if parameters.maxPrice?has_content>
        <#assign serviceCtx = serviceCtx + {"maxPrice": Static["java.math.BigDecimal"].new(parameters.maxPrice)} />
    </#if>
    <#if parameters.productFeatureId?has_content>
        <#assign serviceCtx = serviceCtx + {"productFeatureId": parameters.productFeatureId} />
    </#if>
    <#if parameters.productFeatureTypeId?has_content>
        <#assign serviceCtx = serviceCtx + {"productFeatureTypeId": parameters.productFeatureTypeId} />
    </#if>

    <#assign serviceCtx = serviceCtx + {"userLogin": userLogin!} />

    <#attempt>
        <#assign serviceResult = dispatcher.runSync("findProduct", serviceCtx) />
        <#if serviceResult?? && serviceResult.productList??>
            <#assign productList = serviceResult.productList />
        </#if>
    <#recover>
        <div class="screenlet">
            <div class="screenlet-title-bar">
                <ul><li class="h3">Search Results</li></ul><br class="clear"/>
            </div>
            <div class="screenlet-body">
                <p>Error executing search. Please check your search criteria.</p>
            </div>
        </div>
    </#attempt>

    <div class="screenlet">
        <div class="screenlet-title-bar">
            <ul>
                <li class="h3">Search Results</li>
            </ul>
            <br class="clear"/>
        </div>
        <div class="screenlet-body">
            <#if productList?has_content>
                <#-- Pagination setup -->
                <#assign pageSize = 10 />
                <#assign currentPage = (parameters.VIEW_INDEX!0)?number />
                <#assign totalProducts = productList?size />
                <#assign totalPages = ((totalProducts + pageSize - 1) / pageSize)?floor />
                <#assign startIndex = currentPage * pageSize />
                <#assign endIndex = startIndex + pageSize />
                <#if (endIndex > totalProducts)>
                    <#assign endIndex = totalProducts />
                </#if>

                <div class="button-bar">
                    <span class="label">Found ${totalProducts} product(s). Showing ${startIndex + 1} - ${endIndex}.</span>
                    <#if (totalPages > 1)>
                        <#if (currentPage > 0)>
                            <a href="<@ofbizUrl>FindProduct?noConditionFind=Y&VIEW_INDEX=${currentPage - 1}&productId=${parameters.productId!}&productName=${parameters.productName!}&productCategoryId=${parameters.productCategoryId!}&minPrice=${parameters.minPrice!}&maxPrice=${parameters.maxPrice!}&productFeatureId=${parameters.productFeatureId!}&productFeatureTypeId=${parameters.productFeatureTypeId!}</@ofbizUrl>" class="buttontext">Previous</a>
                        </#if>
                        <#if (currentPage < totalPages - 1)>
                            <a href="<@ofbizUrl>FindProduct?noConditionFind=Y&VIEW_INDEX=${currentPage + 1}&productId=${parameters.productId!}&productName=${parameters.productName!}&productCategoryId=${parameters.productCategoryId!}&minPrice=${parameters.minPrice!}&maxPrice=${parameters.maxPrice!}&productFeatureId=${parameters.productFeatureId!}&productFeatureTypeId=${parameters.productFeatureTypeId!}</@ofbizUrl>" class="buttontext">Next</a>
                        </#if>
                    </#if>
                </div>

                <table class="basic-table hover-bar" cellspacing="0">
                    <tr class="header-row">
                        <th>Product ID</th>
                        <th>Product Name</th>
                        <th>Internal Name</th>
                        <th>Description</th>
                        <th>Price</th>
                        <th>Currency</th>
                        <th>Categories</th>
                        <th>Features</th>
                        <th>Virtual/Variant</th>
                        <th>Actions</th>
                    </tr>
                    <#list productList as product>
                        <#if (product_index >= startIndex) && (product_index < endIndex)>
                            <#assign alt_row = (product_index % 2 == 1)>
                            <tr<#if alt_row> class="alternate-row"</#if>>
                                <td>${product.productId!"-"}</td>
                                <td>${product.productName!"-"}</td>
                                <td>${product.internalName!"-"}</td>
                                <td>${product.description!"-"}</td>
                                <td>${product.price!"-"}</td>
                                <td>${product.currencyUomId!"-"}</td>
                                <td>
                                    <#if product.categoryNames?? && product.categoryNames?has_content>
                                        <#list product.categoryNames as catName>
                                            ${catName!}<#if catName_has_next>, </#if>
                                        </#list>
                                    <#else>
                                        -
                                    </#if>
                                </td>
                                <td>
                                    <#if product.features?? && product.features?has_content>
                                        <#list product.features as feature>
                                            ${feature.description!feature.productFeatureId!}<#if feature_has_next>, </#if>
                                        </#list>
                                    <#else>
                                        -
                                    </#if>
                                </td>
                                <td>
                                    <#if product.isVirtual! == "Y">Virtual</#if>
                                    <#if product.isVariant! == "Y">Variant</#if>
                                    <#if (product.isVirtual! != "Y") && (product.isVariant! != "Y")>-</#if>
                                </td>
                                <td>
                                    <a href="<@ofbizUrl>EditProduct?productId=${product.productId}</@ofbizUrl>" class="buttontext">Edit</a>
                                </td>
                            </tr>
                        </#if>
                    </#list>
                </table>
            <#else>
                <p>No products found matching your criteria. Try broadening your search.</p>
            </#if>
        </div>
    </div>
</#if>
