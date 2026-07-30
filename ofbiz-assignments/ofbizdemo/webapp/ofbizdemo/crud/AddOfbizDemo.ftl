<form method="post" action="<@ofbizUrl>createOfbizDemoEvent</@ofbizUrl>" name="createOfbizDemoEvent">
    <table class="basic-table" cellspacing="0">
        <tbody>
            <tr>
                <td class="label">${uiLabelMap.OfbizDemoType}</td>
                <td>
                    <select id="ofbizDemoTypeId" name="ofbizDemoTypeId">
                        <#list ofbizDemoTypes as demoType>
                            <option value='${demoType.ofbizDemoTypeId}'>${demoType.description}</option>
                        </#list>
                    </select>
                </td>
            </tr>
            <tr>
                <td class="label">${uiLabelMap.OfbizDemoFirstName} *</td>
                <td><input type="text" id="firstName" name="firstName" required></td>
            </tr>
            <tr>
                <td class="label">${uiLabelMap.OfbizDemoLastName} *</td>
                <td><input type="text" id="lastName" name="lastName" required></td>
            </tr>
            <tr>
                <td class="label">${uiLabelMap.OfbizDemoComment}</td>
                <td><input type="text" id="comments" name="comments"></td>
            </tr>
            <tr>
                <td class="label"></td>
                <td><input type="submit" class="smallSubmit" value="${uiLabelMap.CommonAdd}"></td>
            </tr>
        </tbody>
    </table>
</form>
