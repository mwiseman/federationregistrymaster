
<html>
	<head>
		
		<meta name="layout" content="workflow" />
		<title><g:message code="fedreg.view.workflow.script.create.title" /></title>
		
		<script src="${request.contextPath}/js/codemirror/js/codemirror.js" type="text/javascript" charset="utf-8"></script>
	</head>
	<body>
		<section>
			<h2><g:message code="fedreg.view.workflow.script.create.heading" /></h2>
		
			<g:if test="${script.hasErrors()}">
				<div class="error">
				<ul>
				<g:eachError bean="${script}">
				    <li>${it}</li>
				</g:eachError>
				</ul>
				</div>
			</g:if>
		
			<g:form action="save">
				<table>
					<tbody>
						<tr>
							<td><label for="name"><g:message code="label.name" /></label></td>
							<td><g:textField name="name" value="${script.name ?: ''}" /></td>
						</tr>
						<tr>
							<td><label for="description"><g:message code="label.description" /></label></td>
							<td><g:textField name="description" value="${script.description ?: ''}" /></td>
						</tr>
					</tbody>
				</table>
				<td><g:textArea name="definition" value="${(script.definition ?: '// Script definition').encodeAsHTML()}" rows="5" cols="40"/></td>
				<button type="submit" class="button icon icon_add"/><g:message code="label.create" /></button>
			</g:form>
		
			<script type="text/javascript">
				 var textarea = $("#definition");
				  var editor = CodeMirror.fromTextArea('definition',  {
		            height: "300px",
		            content: textarea.value,
		            parserfile: ["tokenizegroovy.js", "parsegroovy.js"],
		            stylesheet: "${request.contextPath}/js/codemirror/css/groovycolors.css",
		            path: "${request.contextPath}/js/codemirror/js/",
		            autoMatchParens: true,
		            disableSpellcheck: true,
		            lineNumbers: true,
		            tabMode: 'shift'
		         });
			</script>
		</section>
	</body>
</html>