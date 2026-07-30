<table class="basic-table hover-bar" cellspacing="0">
    <thead>
        <tr class="header-row">
            <th>${uiLabelMap.OfbizDemoId}</th>
            <th>${uiLabelMap.OfbizDemoType}</th>
            <th>${uiLabelMap.OfbizDemoFirstName}</th>
            <th>${uiLabelMap.OfbizDemoLastName}</th>
            <th>${uiLabelMap.OfbizDemoComment}</th>
        </tr>
    </thead>
    <tbody>
        <#if ofbizDemoList?has_content>
            <#list ofbizDemoList as ofbizDemo>
                <tr>
                    <td>${ofbizDemo.ofbizDemoId}</td>
                    <td>${ofbizDemo.getRelatedOne("OfbizDemoType", false).get("description", locale)}</td>
                    <td>${ofbizDemo.firstName?default("NA")}</td>
                    <td>${ofbizDemo.lastName?default("NA")}</td>
                    <td>${ofbizDemo.comments!}</td>
                </tr>
            </#list>
        </#if>
    </tbody>
</table>
