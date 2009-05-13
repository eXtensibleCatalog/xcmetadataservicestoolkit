<!--
  * Copyright (c) 2009 University of Rochester
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the MIT/X11 license. The text of the  
  * license can be found at http://www.opensource.org/licenses/mit-license.php and copy of the license can be found on the project
  * website http://www.extensiblecatalog.org/. 
  *
  -->

<%@ taglib prefix="c" uri="http://java.sun.com/jstl/core_rt"%>
<%@ taglib prefix="s" uri="/struts-tags" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/functions" prefix="fn" %>

<c:import url="/inc/doctype-frag.jsp"/>

<LINK href="page-resources/css/header.css" rel="stylesheet" type="text/css">
    
<html>
    <head>
        <title>Edit Processing Directive</title>
        <c:import url="/inc/meta-frag.jsp"/>

        <LINK href="page-resources/yui/reset-fonts-grids/reset-fonts-grids.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/base-mst.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/yui/menu/assets/skins/sam/menu.css"  rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/global.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/main_menu.css" rel="stylesheet" type="text/css" >
        <LINK href="page-resources/css/tables.css" rel="stylesheet" type="text/css" >
		<LINK href="page-resources/css/header.css" rel="stylesheet" type="text/css">
		<LINK href="page-resources/css/bodylayout.css" rel="stylesheet" type="text/css">

        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/utilities.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="pages/js/base_path.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/yahoo-dom-event/yahoo-dom-event.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/connection/connection-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" src="page-resources/yui/container/container_core-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/yui/menu/menu-min.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/main_menu.js"></SCRIPT>
        <SCRIPT LANGUAGE="JavaScript" SRC="page-resources/js/edit_processingdirective_sets_formats.js"></SCRIPT>
        
    </head>


 <body class="yui-skin-sam">

        
        <!--  yahoo doc 2 template creates a page 950 pixles wide -->
        <div id="doc2">

            <!-- page header - this uses the yahoo page styling -->
            <div id="hd">

                <!--  this is the header of the page -->
                <c:import url="/inc/header.jsp"/>

                <!--  this is the header of the page -->
                <c:import url="/inc/menu.jsp"/>
                <jsp:include page="/inc/breadcrumb.jsp">

                    <jsp:param name="bread" value="Processing Rules , <a href='listProcessingDirectives.action'><U>List Processing Rules</U></a> , Edit Processing Rules (Step 2)" />

                </jsp:include>
                
            </div>
            <!--  end header -->

            <!-- body -->
            <div id="bd">
                
               <!-- Display of error message -->
                 <c:if test="${errorType != null}">
                    <div id="server_error_div">
                    <div id="server_message_div" class="${errorType}">
                        <img  src="${pageContext.request.contextPath}/page-resources/img/${errorType}.jpg">
                        <s:fielderror cssClass="errorMessage"/>
                    </div>
                    </div>
                 </c:if>
                 <div id="error_div"></div>
                 
                 <div class="clear">&nbsp;</div>

              <c:set var="sessionSourceType" scope="session" value="${sourceType}"/>


                <div class="stepsStructure">
                    <ul style="list-style:none;">
                        <li style="float:left;"><div><img src="page-resources/img/3.4_step1_grey.gif"></div></li>
                        <li style="margin-left:5px;float:left;"><div><img src="page-resources/img/3.4_step2_highlight.gif"></div></li>
                    </ul>
                </div>

                 <div class="StepsHeader">
                    <table width="400">
                        <tr>
                            <td>
                                <div style="margin-right:10px;">
                                    Harvest records from :
                                </div>
                            </td>
                            <td width="5%"></td>
                            <td>
                                <div style="margin-left:15px;">
                                    To be processed by :
                                </div>
                            </td>
                        </tr>
                        <tr>
                            <td>

                            </td>
                            <td width="5%">
                                <div align="center">
                                    <img src="page-resources/img/greenarrow_greybgrd.jpg">
                                </div>
                            </td>
                            <td>

                            </td>
                        </tr>
                        <tr>
                            <td>
                                <div style="margin-right:10px;">
                                    <c:choose>
                                        <c:when test="${sourceType=='provider'}">
                                            <c:set var="sourceDisplay" value="${temporaryProcessingDirective.sourceProvider.name}"/>
                                            <B>${temporaryProcessingDirective.sourceProvider.name}</B>
                                        </c:when>
                                        <c:otherwise>
                                            <c:set var="sourceDisplay" value="${temporaryProcessingDirective.sourceService.name}"/>
                                            <B>${temporaryProcessingDirective.sourceService.name}</B>
                                        </c:otherwise>
                                    </c:choose>
                                    
                                </div>

                            </td>
                            <td width="5%"></td>
                            <td> 
                                <div style="margin-left:15px;">
                                    <B>${temporaryProcessingDirective.service.name}</B>
                                </div>

                            </td>
                        </tr>
                    </table>
                 </div>
              
           <form method="post" name="editProcessingDirectiveSetsFormats">

                    <div style="margin-top:10px;margin-bottom:15px;font-family: verdana,sans-serif;font-size: 13px;">
                        <B>Which records from ${sourceDisplay} should be processed ?</B>
                    </div>

                    <input type="hidden" name="processingDirectiveId" id="processingDirectiveId">

                    <table align="center" cellpadding="0" cellspacing="0" border="0" width="60%">


                        
                        <tr>
                            <td>
                                <c:choose>
                                    <c:when test="${empty formatList}">
                                         <div class="listBox" style="width:300px;">
                                            <B>Formats</B> <br><br>
                                                <I>No Formats to display</I>
                                         </div>
                                    </c:when>
                                    <c:otherwise>
                                        <div class="listBox">
                                            <B>Formats</B> <br>
                                           <select multiple style="width:300px;" size="10" id="formatsSelected" name="formatsSelected">
                                               <c:forEach var="format" items="${formatList}" varStatus="formatCount">
                                                    <c:set var="flag" value="${false}"/>
                                                      <c:forEach var="triggerFormat" items="${temporaryProcessingDirective.triggeringFormats}" varStatus="triggerFormatCount">

                                                            <c:if test="${format.id == triggerFormat.id}">
                                                                <c:set var="flag" value="${true}"/>
                                                            </c:if>

                                                    </c:forEach>
                                                    <c:choose>
                                                        <c:when test="${flag == true}">
                                                             <option selected value="<c:out value="${format.id}"/>" ><c:out value="${format.name}"/> <br>
                                                        </c:when>
                                                         <c:otherwise>
                                                             <option value="<c:out value="${format.id}"/>" ><c:out value="${format.name}"/> <br>
                                                        </c:otherwise>
                                                    </c:choose>
                                                </c:forEach>
                                           </select>

                                        </div>
                                    </c:otherwise>
                                </c:choose>
                            </td>
                      
                            <td>

                               <div style="margin-left:100px;font-family: verdana,sans-serif;font-size: 11px;margin-bottom:20px;margin-top:10px;">
                                    You have the option to create a named set of output records <br>
                                    for this processing directive&nbsp;&nbsp;&nbsp;<br><br>
                                    Output Set Name &nbsp;&nbsp;&nbsp; <input type="text" class="processingDirective_TextBox" name="outputSetName" value="${temporaryProcessingDirective.outputSet.displayName}" maxlength="50">
                                </div>
                                 <div style="margin-left:100px;font-family: verdana,sans-serif;font-size: 11px;margin-bottom:50px;">
                                    Output Set Specification &nbsp;&nbsp;&nbsp;<input type="text" class="processingDirective_TextBox" name="outputSetSpec" value="${temporaryProcessingDirective.outputSet.setSpec}" maxlength="50">
                                </div>

                                
                            </td>
                        </tr>
                        <tr>
                            <td height="10">
                                <c:if test="${!empty setList}">
                                     <div class="smallText">
                                        (CTRL click to select multiple sets)
                                    </div>
                                </c:if>

                            </td>
                        </tr>
                        <tr>
                            <td>
                                <c:choose>
                                    <c:when test="${empty setList}">
                                         <div class="listBox" style="width:300px;">
                                            <B>Sets</B> <br><br>
                                            <I>No Sets to display</I><br><br>
                                         </div>
                                    </c:when>
                                     <c:otherwise>
                                         <div class="listBox">
                                            <B>Sets</B> <br>
                                            <select multiple size="10" style="width:300px;" id="setsSelected" name="setsSelected">
                                                <option value="0"
                                                    <c:if test="${temporaryProcessingDirective.triggeringSets == '[]' || temporaryProcessingDirective.triggeringSets == '[null]'}">
                                                         selected
                                                    </c:if>
                                                >All Sets</option>
                                                <c:forEach var="set" items="${setList}" varStatus="setcount">
                                                    <c:set var="flag" value="${false}"/>
                                                      <c:forEach var="triggerSet" items="${temporaryProcessingDirective.triggeringSets}" varStatus="triggerSetCount">

                                                            <c:if test="${set.id == triggerSet.id}">
                                                                <c:set var="flag" value="${true}"/>

                                                            </c:if>

                                                    </c:forEach>
                                                    <c:choose>
                                                        <c:when test="${flag==true}">
                                                             <option selected value="<c:out value="${set.id}"/>" >${set.displayName}(${set.setSpec})
                                                        </c:when>
                                                         <c:otherwise>
                                                             <option value="<c:out value="${set.id}"/>" >${set.displayName}(${set.setSpec})
                                                        </c:otherwise>
                                                    </c:choose>
                                                </c:forEach>
                                            </select>

                                         </div>
                                     </c:otherwise>
                                 </c:choose>

                            </td>
                        </tr>
                        <tr>
                            <td height="10">
                                <c:if test="${!empty formatList}">
                                     <div class="smallText">
                                        (CTRL click to select multiple formats)
                                    </div>
                                </c:if>

                            </td>
                        </tr>
                        
                        <tr>
                            <td colspan="2">
                                
                                <hr size="1" style="color:#cfd2d4"><br>
                                <div align="right">
                                  <button style="vertical-align:bottom;" class="xc_button_small" type="button" onclick="javascript:YAHOO.xc.mst.processingDirective.editDirectiveSetsFormats.cancel();" name="cancel">Cancel</button> &nbsp;&nbsp;&nbsp;
                                  <button class="xc_button" type="button" onclick="javascript:YAHOO.xc.mst.processingDirective.editDirectiveSetsFormats.goBack();" name="goBack">Back to Step 1</button> &nbsp;&nbsp;&nbsp;
                                  <button class="xc_button" type="button" onclick="javascript:YAHOO.xc.mst.processingDirective.editDirectiveSetsFormats.editProcessingDirective();"name="editDirective">Finish</button>
                                </div>
                            </td>
                        </tr>
                    </table>

                </form>
            </div>
       </div>
</body>
</html>
