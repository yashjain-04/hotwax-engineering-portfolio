<#-- Find Customer FTL -->
<div class="screenlet">
    <div class="screenlet-title-bar">
        <h3>Find Customer</h3>
    </div>
    <div class="screenlet-body">
        <form id="searchForm" name="searchForm" action="<@ofbizUrl>findCustomerService</@ofbizUrl>" method="post">
            <table>
                <tr>
                    <td class="label">Party ID</td>
                    <td><input type="text" name="partyId" id="searchPartyId"/></td>
                </tr>
                <tr>
                    <td class="label">Email Address</td>
                    <td><input type="text" name="emailAddress" id="emailAddress"/></td>
                </tr>
                <tr>
                    <td class="label">First Name</td>
                    <td><input type="text" name="firstName" id="firstName"/></td>
                </tr>
                <tr>
                    <td class="label">Last Name</td>
                    <td><input type="text" name="lastName" id="lastName"/></td>
                </tr>
                <tr>
                    <td class="label">Phone Number</td>
                    <td><input type="text" name="contactNumber" id="contactNumber"/></td>
                </tr>
                <tr>
                    <td class="label">Address</td>
                    <td><input type="text" name="address1" id="address1"/></td>
                </tr>
                <tr>
                    <td class="label">City</td>
                    <td><input type="text" name="city" id="city"/></td>
                </tr>
                <tr>
                    <td></td>
                    <td><input type="button" value="Search" onclick="searchCustomers()"/></td>
                </tr>
            </table>
        </form>
    </div>
</div>

<div class="screenlet">
    <div class="screenlet-title-bar">
        <h3>Create/Update Customer</h3>
    </div>
    <div class="screenlet-body">
        <form id="createForm" name="createForm" action="<@ofbizUrl>createCustomerEvent</@ofbizUrl>" method="post">
            <table>
                <tr>
                    <td class="label">Email Address (Unique)</td>
                    <td><input type="text" name="emailAddress" required="required"/></td>
                </tr>
                <tr>
                    <td class="label">First Name</td>
                    <td><input type="text" name="firstName" required="required"/></td>
                </tr>
                <tr>
                    <td class="label">Last Name</td>
                    <td><input type="text" name="lastName" required="required"/></td>
                </tr>
                <tr>
                    <td class="label">Phone Number</td>
                    <td><input type="text" name="contactNumber"/></td>
                </tr>
                <tr>
                    <td class="label">Address</td>
                    <td><input type="text" name="address1"/></td>
                </tr>
                <tr>
                    <td class="label">City</td>
                    <td><input type="text" name="city"/></td>
                </tr>
                <tr>
                    <td></td>
                    <td>
                        <input type="submit" value="Create Customer"/>
                        <input type="button" value="Update Customer" onclick="updateCustomer()"/>
                    </td>
                </tr>
            </table>
        </form>
    </div>
</div>

<div class="screenlet">
    <div class="screenlet-title-bar">
        <h3>Search Results</h3>
    </div>
    <div class="screenlet-body">
        <table class="basic-table" id="resultsTable">
            <thead>
                <tr>
                    <th>Party ID</th>
                    <th>Email Address</th>
                    <th>First Name</th>
                    <th>Last Name</th>
                    <th>Phone Number</th>
                    <th>Address</th>
                    <th>City</th>
                </tr>
            </thead>
            <tbody>
                <!-- Results populated via JS -->
            </tbody>
        </table>
        <div id="paginationControls" style="display:none; text-align:right; margin-top:10px;">
            <input type="button" value="Previous" onclick="previousPage()"/>
            <span id="pageInfo" style="margin:0 10px;"></span>
            <input type="button" value="Next" onclick="nextPage()"/>
        </div>
    </div>
</div>

<script type="application/javascript">
    var allCustomers = [];
    var currentPage = 1;
    var pageSize = 5;

    function searchCustomers() {
        var formData = jQuery("#searchForm").serialize();
        jQuery.ajax({
            url: "<@ofbizUrl>findCustomerService</@ofbizUrl>",
            type: "POST",
            data: formData,
            success: function(data) {
                if (data._ERROR_MESSAGE_) {
                    alert("Error: " + data._ERROR_MESSAGE_);
                    return;
                }
                if (data._ERROR_MESSAGE_LIST_ && data._ERROR_MESSAGE_LIST_.length > 0) {
                    alert("Error: " + data._ERROR_MESSAGE_LIST_[0]);
                    return;
                }
                if (data.customers && data.customers.length > 0) {
                    allCustomers = data.customers;
                } else {
                    allCustomers = [];
                }
                currentPage = 1;
                renderTable();
            },
            error: function(xhr, status, error) {
                alert("An error occurred: " + error);
            }
        });
    }

    function renderTable() {
        var tbody = jQuery("#resultsTable tbody");
        tbody.empty();
        
        if (allCustomers.length === 0) {
            tbody.append("<tr><td colspan='7'>No customers found</td></tr>");
            jQuery("#paginationControls").hide();
            return;
        }
        
        var startIndex = (currentPage - 1) * pageSize;
        var endIndex = startIndex + pageSize;
        var pageCustomers = allCustomers.slice(startIndex, endIndex);
        
        pageCustomers.forEach(function(cust) {
            var row = "<tr>" +
                "<td>" + (cust.partyId || "") + "</td>" +
                "<td>" + (cust.emailAddress || "") + "</td>" +
                "<td>" + (cust.firstName || "") + "</td>" +
                "<td>" + (cust.lastName || "") + "</td>" +
                "<td>" + (cust.contactNumber || "") + "</td>" +
                "<td>" + (cust.address1 || "") + "</td>" +
                "<td>" + (cust.city || "") + "</td>" +
            "</tr>";
            tbody.append(row);
        });
        
        jQuery("#paginationControls").show();
        jQuery("#pageInfo").text("Page " + currentPage + " of " + Math.ceil(allCustomers.length / pageSize));
    }
    
    function previousPage() {
        if (currentPage > 1) {
            currentPage--;
            renderTable();
        }
    }
    
    function nextPage() {
        if (currentPage < Math.ceil(allCustomers.length / pageSize)) {
            currentPage++;
            renderTable();
        }
    }

    function updateCustomer() {
        var form = document.getElementById('createForm');
        form.action = "<@ofbizUrl>updateCustomerEvent</@ofbizUrl>";
        form.submit();
    }
</script>
