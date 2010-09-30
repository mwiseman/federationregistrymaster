package fedreg.core

import org.springframework.context.i18n.LocaleContextHolder as LCH
import fedreg.workflow.ProcessPriority

class SPSSODescriptorService {

	def cryptoService
	def entityDescriptorService
	def workflowProcessService

	def create(def params) {
		// Organization
		def organization = Organization.get(params.organization?.id)

		// Contact
		def contact = Contact.get(params.contact?.id)
		if(!contact) {
			if(params.contact?.email)
				contact = MailURI.findByUri(params.contact?.email)?.contact		// We may already have them referenced by email address and user doesn't realize
			if(!contact)
				contact = new Contact(givenName: params.contact?.givenName, surname: params.contact?.surname, email: new MailURI(uri:params.contact?.email), organization:organization)
				contact.save()
				if(contact.hasErrors()) {
					contact.errors.each {
						log.error it
					}
					throw new RuntimeException("Unable to create new contact when attempting to create new SPSSODescriptor")
				}
		}
		def ct = params.contact?.type ?: 'administrative'
		
		// Entity Descriptor
		def entityDescriptor
		if(params.entity?.id) {		
			entityDescriptor = EntityDescriptor.get(params.entity?.id)
		}
		
		if(!entityDescriptor) {
			def created
			(created, entityDescriptor) = entityDescriptorService.create(params)	// If it doesn't create we don't really care it is caught below
		}
		
		// SP
		def samlNamespace = SamlURI.findByUri('urn:oasis:names:tc:SAML:2.0:protocol')
		def serviceProvider = new SPSSODescriptor(approved:false, active:params.active, displayName: params.sp?.displayName, description: params.sp?.description, organization: organization, authnRequestsSigned:true, wantAssertionsSigned: true)
		serviceProvider.addToProtocolSupportEnumerations(samlNamespace)
		
		def acs = new AttributeConsumingService(approved:true, lang:params.lang ?:'en')
		acs.addToServiceNames(params.sp?.displayName ?: '')
		acs.addToServiceDescriptions(params.sp?.description ?: '')
		params.sp.attributes.each { a -> 
			def attrID = a.key
			def val = a.value
			
			if(!( val instanceof String )) {					// org.codehaus.groovy.grails.web.servlet.mvc.GrailsParameterMap oddities where vals are both in maps and individually presented as Strings
				if(val.requested && val.requested == "on") {
					def attr = AttributeBase.get(attrID)
					if(attr) {
						def ra = new RequestedAttribute(approved:true, base:attr, reasoning: val.reasoning, isRequired: (val.required == 'on') )
						acs.addToRequestedAttributes(ra)
						
						if(val.requestedvalues) {
							val.requestedvalues.each {
								if(it.value && it.value.size() > 0)
									ra.addToValues( new AttributeValue(value: it.value) )
							}
						}
					}
				}
			}
		}
		serviceProvider.addToAttributeConsumingServices(acs)
		
		params.sp.nameidformats.each { nameFormatID -> 
			if(nameFormatID.value == "on") {
				def nameid = SamlURI.get(nameFormatID.key)
				if(nameid)
					serviceProvider.addToNameIDFormats(nameid)
			}
		}
		def spContactPerson = new ContactPerson(contact:contact, type:ContactType.findByName(ct))
		serviceProvider.addToContacts(spContactPerson)
		
		// Initial endpoints
		// Assertion Consumer Services
		def postBinding = SamlURI.findByUri(SamlConstants.httpPost)
		def postLocation = new UrlURI(uri: params.sp?.acs?.post?.uri)
		def httpPostACS = new AssertionConsumerService(approved: true, binding: postBinding, location:postLocation, active:params.active)
		serviceProvider.addToAssertionConsumerServices(httpPostACS)
		httpPostACS.validate()

		def artifactBinding = SamlURI.findByUri(SamlConstants.soap)
		def artifactLocation = new UrlURI(uri: params.sp?.acs?.artifact?.uri)
		def soapArtifactACS = new AssertionConsumerService(approved: true, binding: artifactBinding, location:artifactLocation, active:params.active)
		serviceProvider.addToAssertionConsumerServices(soapArtifactACS)
		soapArtifactACS.validate()
		
		// Single Logout Services
		def sloArtifact, sloRedirect, sloSOAP, sloPost
		if(params.sp?.slo?.artifact?.uri){
			def sloArtifactBinding = SamlURI.findByUri(SamlConstants.httpArtifact)
			def sloArtifactLocation = new UrlURI(uri: params.sp?.slo?.artifact?.uri)
			sloArtifact = new SingleLogoutService(approved: true, binding: sloArtifactBinding, location:sloArtifactLocation, active:params.active, isDefault:params.sp?.slo?.artifact?.isdefault)
			serviceProvider.addToSingleLogoutServices(sloArtifact)
			sloArtifact.validate()
		}
		if(params.sp?.slo?.redirect?.uri){
			def sloRedirectBinding = SamlURI.findByUri(SamlConstants.httpRedirect)
			def sloRedirectLocation = new UrlURI(uri: params.sp?.slo?.redirect?.uri)
			sloRedirect	= new SingleLogoutService(approved: true, binding: sloRedirectBinding, location:sloRedirectLocation, active:params.active, isDefault:params.sp?.slo?.redirect?.isdefault)
			serviceProvider.addToSingleLogoutServices(sloRedirect)
			sloRedirect.validate()
		}
		if(params.sp?.slo?.soap?.uri){
			def sloSOAPBinding = SamlURI.findByUri(SamlConstants.soap)
			def sloSOAPLocation = new UrlURI(uri: params.sp?.slo?.soap?.uri)
			sloSOAP = new SingleLogoutService(approved: true, binding: sloSOAPBinding, location:sloSOAPLocation, active:params.active, isDefault:params.sp?.slo?.soap?.isdefault)
			serviceProvider.addToSingleLogoutServices(sloSOAP)
			sloSOAP.validate()
		}
		if(params.sp?.slo?.post?.uri){
			def sloPostBinding = SamlURI.findByUri(SamlConstants.httpPost)
			def sloPostLocation = new UrlURI(uri: params.sp?.slo?.post?.uri)
			sloPost = new SingleLogoutService(approved: true, binding: sloPostBinding, location:sloPostLocation, active:params.active, isDefault:params.sp?.slo?.post?.isdefault)
			serviceProvider.addToSingleLogoutServices(sloPost)
			sloPost.validate()
		}
		
		// Manage NameID Services - optional
		def mnidArtifact, mnidRedirect, mnidSOAP, mnidPost
		if(params.sp?.mnid?.artifact?.uri){
			def mnidArtifactBinding = SamlURI.findByUri(SamlConstants.httpArtifact)
			def mnidArtifactLocation = new UrlURI(uri: params.sp?.mnid?.artifact?.uri)
			mnidArtifact = new ManageNameIDService(approved: true, binding: mnidArtifactBinding, location:mnidArtifactLocation, active:params.active, isDefault:params.sp?.mnid?.artifact?.isdefault)
			serviceProvider.addToManageNameIDServices(mnidArtifact)
			mnidArtifact.validate()
		}
		if(params.sp?.mnid?.redirect?.uri){
			def mnidRedirectBinding = SamlURI.findByUri(SamlConstants.httpRedirect)
			def mnidRedirectLocation = new UrlURI(uri: params.sp?.mnid?.redirect?.uri)
			mnidRedirect	= new ManageNameIDService(approved: true, binding: mnidRedirectBinding, location:mnidRedirectLocation, active:params.active, isDefault:params.sp?.mnid?.redirect?.isdefault)
			serviceProvider.addToManageNameIDServices(mnidRedirect)
			mnidRedirect.validate()
		}
		if(params.sp?.mnid?.soap?.uri){
			def mnidSOAPBinding = SamlURI.findByUri(SamlConstants.soap)
			def mnidSOAPLocation = new UrlURI(uri: params.sp?.mnid?.soap?.uri)
			mnidSOAP = new ManageNameIDService(approved: true, binding: mnidSOAPBinding, location:mnidSOAPLocation, active:params.active, isDefault:params.sp?.mnid?.soap?.isdefault)
			serviceProvider.addToManageNameIDServices(mnidSOAP)
			mnidSOAP.validate()
		}
		if(params.sp?.mnid?.post?.uri){
			def mnidPostBinding = SamlURI.findByUri(SamlConstants.httpPost)
			def mnidPostLocation = new UrlURI(uri: params.sp?.mnid?.post?.uri)
			mnidPost = new ManageNameIDService(approved: true, binding: mnidPostBinding, location:mnidPostLocation, active:params.active, isDefault:params.sp?.mnid?.post?.isdefault)
			serviceProvider.addToManageNameIDServices(mnidPost)
			mnidPost.validate()
		}
		
		// Discovery Response Services - optional
		def discoveryResponseService
		if(params.sp?.drs?.uri){
			def drsBinding = SamlURI.findByUri(SamlConstants.drs)
			def drsLocation = new UrlURI(uri: params.sp?.drs?.uri)
			discoveryResponseService = new DiscoveryResponseService(approved: true, binding: drsBinding, location:drsLocation, active:params.active, isDefault:params.sp?.drs.isdefault)
			serviceProvider.addToDiscoveryResponseServices(discoveryResponseService)
			discoveryResponseService.validate()
		}
		
		// Cryptography
		// Signing
		if(params.sp?.crypto?.sig) {
			def cert = cryptoService.createCertificate(params.sp?.crypto?.sigdata)
			cryptoService.validateCertificate(cert)
			def keyInfo = new KeyInfo(certificate: cert)
			def keyDescriptor = new KeyDescriptor(keyInfo:keyInfo, keyType:KeyTypes.signing, roleDescriptor:serviceProvider)
			serviceProvider.addToKeyDescriptors(keyDescriptor)
		}
		
		// Encryption
		if(params.sp?.crypto?.enc) {
			def certEnc = cryptoService.createCertificate(params.sp?.crypto?.encdata)
			cryptoService.validateCertificate(certEnc)
			def keyInfoEnc = new KeyInfo(certificate:certEnc)
			def keyDescriptorEnc = new KeyDescriptor(keyInfo:keyInfoEnc, keyType:KeyTypes.encryption, roleDescriptor:serviceProvider)
			serviceProvider.addToKeyDescriptors(keyDescriptorEnc)
		}
		
		// Service Description
		def serviceDescription = new ServiceDescription(connectURL: params.sp?.servicedescription?.connecturl, logo: params.sp?.servicedescription?.logo, furtherInfo: params.sp?.servicedescription?.furtherInfo, 
			provides: params.sp?.servicedescription?.provides, benefits: params.sp?.servicedescription?.benefits, audience: params.sp?.servicedescription?.audience, restrictions: params.sp?.servicedescription?.restrictions, 
			accessing: params.sp?.servicedescription?.accessing, support: params.sp?.servicedescription?.support, maintenance: params.sp?.servicedescription?.maintenance)
		serviceProvider.serviceDescription = serviceDescription
		
		// Submission validation
		if(!entityDescriptor.save()) {
			entityDescriptor?.errors.each { log.error it }
			return [false, organization, entityDescriptor, serviceProvider, httpPostACS, soapArtifactACS, sloArtifact, sloRedirect, sloSOAP, sloPost, Organization.list(), AttributeBase.list(), SamlURI.findAllWhere(type:SamlURIType.ProtocolBinding), contact]
		}
		serviceProvider.entityDescriptor = entityDescriptor
		entityDescriptor.addToSpDescriptors(serviceProvider)
		
		if(!serviceProvider.validate()) {			
			serviceProvider.errors.each { log.warn it }
			return [false, organization, entityDescriptor, serviceProvider, httpPostACS, soapArtifactACS, sloArtifact, sloRedirect, sloSOAP, sloPost, Organization.list(), AttributeBase.list(), SamlURI.findAllWhere(type:SamlURIType.ProtocolBinding), contact]
		}
		
		if(!serviceProvider.save()) {			
			serviceProvider.errors.each {log.warn it}
			throw new RuntimeException("Unable to save when creating ${serviceProvider}")
		}
		
		def workflowParams = [ creator:contact?.id?.toString(), serviceProvider:serviceProvider?.id?.toString(), organization:organization.id?.toString(), locale:LCH.getLocale().getLanguage() ]
		def (initiated, processInstance) = workflowProcessService.initiate( "spssodescriptor_create", "Approval for creation of ${serviceProvider}", ProcessPriority.MEDIUM, workflowParams)
		
		if(initiated)
			workflowProcessService.run(processInstance)
		else
			throw new RuntimeException("Unable to execute workflow when creating ${serviceProvider}")
		
		return [true, organization, entityDescriptor, serviceProvider, httpPostACS, soapArtifactACS, sloArtifact, sloRedirect, sloSOAP, sloPost, Organization.list(), AttributeBase.list(), SamlURI.findAllWhere(type:SamlURIType.ProtocolBinding), contact]
	}
	
	
	def update(def params) {
		def serviceProvider = SPSSODescriptor.get(params.id)
		if(!serviceProvider)
			return [false, null]
		
		serviceProvider.displayName = params.sp.displayName
		serviceProvider.description = params.sp.description
		
		serviceProvider.serviceDescription.connectURL = params.sp?.servicedescription?.connecturl
		serviceProvider.serviceDescription.logoURL = params.sp?.servicedescription?.logo
		serviceProvider.serviceDescription.furtherInfo = params.sp?.servicedescription?.furtherinfo
		serviceProvider.serviceDescription.provides = params.sp?.servicedescription?.provides
		serviceProvider.serviceDescription.benefits = params.sp?.servicedescription?.benefits
		serviceProvider.serviceDescription.audience = params.sp?.servicedescription?.audience
		serviceProvider.serviceDescription.restrictions = params.sp?.servicedescription?.restrictions
		serviceProvider.serviceDescription.accessing = params.sp?.servicedescription?.accessing
		serviceProvider.serviceDescription.support = params.sp?.servicedescription?.support
		serviceProvider.serviceDescription.maintenance = params.sp?.servicedescription?.maintenance
		
		if(!serviceProvider.validate()) {			
			serviceProvider.errors.each {log.warn it}
			return [false, serviceProvider]
		}
		
		if(!serviceProvider.save()) {			
			serviceProvider.errors.each {log.warn it}
			throw new RuntimeException("Unable to save when updating ${serviceProvider}")
		}
		
		return [true, serviceProvider]
	}

}