<%-- Copyright (C) 2022 Azquo Ltd. --%>
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<c:set var="title" scope="request" value="Import Wizard"/>
<%@ include file="../includes/admin_header2.jsp" %>
<script>
    /*
    const fileInput = document.querySelector('#file-js-example input[type=file]');
    fileInput.onchange = () => {
        if (fileInput.files.length > 0) {
            const fileName = document.getElementById("filename");
            if (fileInput.files.length > 1) {
                fileName.textContent = "Multiple files selected";
            } else {
                fileName.textContent = fileInput.files[0].name;
            }
        }
    }
    */


    var selectedItem = null;


    function selectionChange(fieldName) {
        if (selectedItem != null) {
            document.getElementById(selectedItem).style.fontWeight = null;
        }
        selectedItem = fieldName;
        document.getElementById(selectedItem).style.fontWeight = "bold";
        var selectedValue = document.getElementById(fieldName).value;
        document.getElementById("valueSelected").value = selectedValue;
        document.getElementById("fieldSelected").value = fieldName;

    }


</script>


<div class="box">
    <h1 class="title">Import Wizard</h1>
    <div class="has-text-danger">${error}</div>
    <form action="/api//ImportWizard" method="post" id="wform" enctype="multipart/form-data">
        <input type="hidden" name="fieldSelected" id="fieldSelected" value=""/>
        <input type="hidden" name="valueSelected" id="valueSelected" value=""/>
        <input type="hidden" name="stage" id="stage" value="${stage}"/>
        <!-- no business id -->
        <table>
            <tr>
                <c:if test="${stage>1}">
                    <td>
                        <a href="/api//CreateExcelForDownload?action=DOWNLOADIMPORTWIZARD" class="centeralign">Download
                            work so far</span> </a>
                    </td>
                </c:if>
                <td>
                    <div class="file has-name is-small" id="file-js-example">

                        <label class="file-label">
                            <input class="file-input is-small" type="file" name="uploadFile"
                                   id="uploadFile">
                            <span class="file-cta is-small">
                                              <span class="file-icon is-small">
                                                <i class="fas fa-upload"></i>
                                              </span>
                                              <span class="file-label is-small">
                                                  <c:if test="${stage==1}">Select File to upload</c:if>
                                                   <c:if test="${stage>1}">Reload saved work</c:if>
                                            </span>
                                            </span>
                            <span class="file-name is-small" id="filename">
                                            </span>
                        </label>
                    </div>
                </td>
                <td>
                    <span id="stageheading" class="content">${stageheading}</span>
                </td>
                <td style="width:30px"></td>
                <td>
                    <span id="stageexplanation" class="content">${stageexplanation}</span>
                </td>
            </tr>
        </table>
        <c:if test="${stage>1}">

            <table class="table">
                <tbody>
                <tr>
                <tr>
                    <td>Heading</td>
                    <td>Values found - select to find associated values</td>
                    <td>Suggested name</td>
                    <td>
                        <c:if test="${stage>2}">
                            Field type
                        </c:if>
                        <c:if test="${stage==5}">
                           Select peers
                        </c:if>
                        <c:if test="${stage==6}">
                            Mark fields that are PARENTS
                        </c:if>

                    </td>
                         <td rowspan="${fieldCount}">
                        <div>
                            <c:if test="${stage==4}">
                                <label class="label">New data type</label><input type="text" name="newparent"
                                                                                 value="${dataparent}"/>
                                <c:choose>
                                    <c:when test="${existingparents.size()>0 && dataparent==null}">

                                        <label class="label"> or revise existing type </label>
                                        <select name="existingparent" onchange="submitDocument()">
                                            <option></option>
                                            <c:forEach items="${existingparents}" var="existingparent">
                                                <option onclick=clickget(value="${existingparent}")>${existingparent}</option>
                                            </c:forEach>

                                        </select>
                                    </c:when>
                                 </c:choose>
                            </c:if>
                            <c:if test="${stage==5}">
                                <label class="label">Data type: ${dataparent}</label>
                                <input type="hidden" name="newparent" value="${dataparent}"/>
                                <c:choose>
                                    <c:when test="${existingparents.size()>1}">

                                        <label class="label"> or revise existing type </label>
                                        <select name="existingparent" onchange="submitDocument()">
                                             <c:forEach items="${existingparents}" var="existingparent">
                                                <option onclick=clickget(value="${existingparent}")>${existingparent}</option>
                                            </c:forEach>

                                        </select>
                                    </c:when>
                                </c:choose>
                            </c:if>
                            <c:if test="${stage==8}">
                                <label class="label">${progressmessage}</label>
                                <div class="centeralign">>
                                <button type="submit" name="btnsubmit" value="makedb" class="button is-small">Create database and reports</button>
                                <button type="submit" name="btnsubmit" value="specials" class="button is-small">Add special instructions </button>
                                </div>
                            </c:if>

                        </div>
                     </td>
                </tr>

                <c:forEach items="${fields.keySet()}" var="field" varStatus="loop">
                    <c:set var="wizardField" value="${fields.get(field)}"/>
                    <tr>
                        <td>
                            <c:choose>
                                <c:when test="${stage==2 && loop.index==fieldCount}">
                                    <input name="newfieldname" type="text" value="${newfieldname}"/>
                                </c:when>
                                <c:otherwise>
                                    <label class="label">${wizardField.importedName}</label>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td style="width:50px;overflow:hidden">
                            <c:choose>
                                <c:when test="${wizardField.valuesFound.size() == 0}">
                                </c:when>
                                <c:when test="${wizardField.valuesFound.size() == 1 && field.equals(headingChosen)}">
                                    <b>${wizardField.valuesFound.get(0)}</b>
                                </c:when>
                                <c:when test="${wizardField.valuesFound.size() == 1}">
                                    ${wizardField.valuesFound.get(0)}
                                </c:when>
                                <c:otherwise>

                                    <select name="${field}" onfocus=selectionChange("${field}")
                                            onchange=selectionChange("${field}") id="${field}">
                                        <c:forEach items="${wizardField.valuesFound}" var="instance">
                                            <option onclick=clickget(value="${instance}")>${fn:substring(instance,0,30)}</option>
                                        </c:forEach>
                                    </select>
                                </c:otherwise>
                            </c:choose>
                        </td>
                        <td>
                            <c:if test="${stage==2}">
                                <input type="text" value="${wizardField.name}" name="name_${field}"/>
                            </c:if>
                            <c:if test="${stage>2}">${wizardField.name}
                            </c:if>

                        </td>
                        <c:if test="${stage==2}">
                            <td>
                                <c:if test="${fieldCount>loop.index}">

                                    <input type="checkbox" name="ignore_${field}" value="true" <c:if
                                            test="${wizardField.ignore}"> checked </c:if>>
                                    <label for="ignore_${field}">
                                        <c:choose>

                                            <c:when test="${wizardField.added==true}">
                                                Delete
                                            </c:when>
                                            <c:otherwise>
                                                Do not upload
                                            </c:otherwise>

                                        </c:choose>
                                    </label>
                                </c:if>
                            </td>
                        </c:if>
                        <c:if test="${stage==3}">
                            <td>
                                <select name="type_${field}">
                                    <option value="null"></option>
                                    <c:forEach items="${options}" var="type">
                                    <option value="${type}"
                                            <c:if test="${wizardField.type.equals(type)}"> selected </c:if>>${type}</option>
                                    </c:forEach>
                                </select>
                            </td>
                        </c:if>
                        <c:if test="${stage==4}">
                            <td>
                                <c:choose>
                                    <c:when test="${\"date\".equals(wizardField.type)|| \"time\".equals(wizardField.type)}">${wizardField.type}</c:when>
                                    <c:when test="${\"data\".equals(wizardField.type)&& (dataparent==null || !dataparent.equals(wizardField.parent))}">${wizardField.interpretation}</c:when>
                                    <c:otherwise>
                                        <input type="checkbox" name="child_${field}" value="true"
                                        <c:if test="${wizardField.parent!=null && wizardField.parent.equals(dataparent)}">
                                               checked </c:if>>
                                        <label for="child_${field}">Select Data</label>
                                    </c:otherwise>
                                </c:choose>

                            </td>
                        </c:if>
                        <c:if test="${stage==5}">
                            <td>
                                <c:choose>
                                    <c:when test="${fn:contains(potentialPeers,field)}">
                                        <input type="checkbox" name="peer_${field}" value="true"
                                        <c:if test="${fn:contains(peersChosen,field)}"> checked </c:if>>
                                        <label for="child_${field}">Select Peer</label>
                                    </c:when>
                                    <c:when test="${\"data\".equals(wizardField.type)}">${wizardField.interpretation}</c:when>

                                </c:choose>
                            </td>
                        </c:if>
                        <c:if test="${stage==6}">
                            <td>
                                <c:choose>
                                    <c:when test="${fn:contains(undefinedFields,field)}">
                                        <select name="child_${field}">
                                            <option value=""></option>
                                            <c:forEach items="${possibleChildFields}" var="child">
                                                <c:if test="${!child.equals(field)}">
                                                <option value="${child}"
                                                        <c:if test="${child.equals(wizardField.child)}"> selected </c:if>>${fields.get(child).name}</option>
                                                </c:if>
                                            </c:forEach>
                                        </select>
                                      </c:when>
                                    <c:otherwise>
                                       ${wizardField.interpretation}
                                    </c:otherwise>
                                </c:choose>
                            </td>
                        </c:if>
                        <c:if test="${stage==7}">
                            <td>
                                <c:choose>
                                    <c:when test="${fn:contains(possibleAttributeFields,field)}">
                                        <select name="attribute_${field}">
                                            <option value=""></option>
                                            <c:forEach items="${possibleAnchorFields}" var="anchor">
                                                <option value="${anchor}"
                                                            <c:if test="${anchor.equals(wizardField.anchor)}"> selected </c:if>>${fields.get(anchor).name}</option>
                                             </c:forEach>
                                        </select>
                                    </c:when>
                                    <c:otherwise>
                                        ${wizardField.interpretation}
                                    </c:otherwise>
                                </c:choose>
                            </td>
                        </c:if>
                        <c:if test="${stage==8}">
                            <td>
                                    ${wizardField.interpretation}
                            </td>
                        </c:if>


                    </tr>
                </c:forEach>
                </tbody>
            </table>

        </c:if>


        <div class="centeralign">
            <c:if test="${stage>1}">
                <button type="submit" name="btnsubmit" value="last" class="button is-small">Last</button>
                <button type="submit" name="btnsubmit" value="reload" class="button is-small">Re-show </button>
             </c:if>
            <c:if test="${stage<8}">
                <button type="submit" name="btnsubmit" value="next" class="button is-small">Next</button>
            </c:if>
        </div>
    </form>
    <script>
        function submitDocument() {
            document.getElementById("wform").submit();
        }


    </script>

</div>

<%@ include file="../includes/admin_footer.jsp" %>
