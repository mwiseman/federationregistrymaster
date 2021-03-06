import fedreg.core.*

workflowTaskService = ctx.getBean("workflowTaskService")
mailService = ctx.getBean("mailService")
messageSource = ctx.getBean("messageSource")

def idp = IDPSSODescriptor.get(env.identityProvider.toLong())

if(idp) {	
	def creator = Contact.get(env.creator.toLong())
	mailService.sendMail {
		to creator.email.uri
		from ctx.grailsApplication.config.nimble.messaging.mail.from
		subject messageSource.getMessage("fedreg.templates.mail.workflow.idp.registered.subject", null, "fedreg.templates.mail.workflow.idp.registered.subject", new Locale(env.locale))
		body view:"/templates/mail/workflows/default/_registered_idp", model:[identityProvider:idp, locale:env.locale]
	}

	workflowTaskService.complete(env.taskInstanceID.toLong(), 'confirmedidpssodescriptor')
}
else {
	throw new RuntimeException("Attempt to email confirmation in script idpssodescriptor_confirm. Failed because referenced IDP does not exist")
}