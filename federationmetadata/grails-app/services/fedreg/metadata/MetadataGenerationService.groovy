package fedreg.metadata

import java.text.SimpleDateFormat
import org.springframework.transaction.annotation.*
import fedreg.core.*

class MetadataGenerationService {
	
	def localizedName(builder, type, lang, content) {
		builder."$type"('xml:lang':lang, content)
	}

	def localizedUri(builder, type, lang, content) {
		builder."$type"('xml:lang':lang, content)
	}
	
	def organization(builder, organization) {
		builder.Organization() {
			localizedName(builder, "OrganizationName", organization.lang, organization.name)
			localizedName(builder, "OrganizationDisplayName", organization.lang, organization.displayName)
			localizedUri(builder, "OrganizationURL", organization.lang, organization.url.uri)
		}
	}
	
	def contactPerson(builder, contactPerson) {
		// If a contact is anything other then scehma valid they get set to other
		def contactType
		if(["technical", "support", "administrative", "billing", "other"].contains(contactPerson.type.name))
			contactType = contactPerson.type.name
		else
			contactType = "other"
			
		builder.ContactPerson(contactType:contactType) {
			if(contactPerson.contact.organization)
				Company(contactPerson.contact.organization.displayName)
			GivenName(contactPerson.contact.givenName)
			SurName(contactPerson.contact.surname)
			EmailAddress(contactPerson.contact.email.uri)
			if(contactPerson.contact.workPhone)
				TelephoneNumber(contactPerson.contact.workPhone.uri)
			if(contactPerson.contact.homePhone)
				TelephoneNumber(contactPerson.contact.homePhone.uri)
			if(contactPerson.contact.mobilePhone)
				TelephoneNumber(contactPerson.contact.mobilePhone.uri)
		}
	}
	
	def caKeyInfo(builder, keyInfo) {
		builder.'ds:KeyInfo'('xmlns:ds':'http://www.w3.org/2000/09/xmldsig#') {
			if(keyInfo.keyName)
				'ds:KeyName'(keyInfo.keyName)
			'ds:X509Data'() {
				def data = keyInfo.certificate.data
				if(data.startsWith('-----BEGIN CERTIFICATE-----'))
					data = data.replace('-----BEGIN CERTIFICATE-----', '')
				if(data.endsWith('-----END CERTIFICATE-----'))
					data = data.replace('-----END CERTIFICATE-----', '')
					
				'ds:X509Certificate'(data.normalize())
			}
		}
	}
	
	def keyInfo(builder, keyInfo) {
		builder.'ds:KeyInfo'('xmlns:ds':'http://www.w3.org/2000/09/xmldsig#') {
			if(keyInfo.keyName)
				'ds:KeyName'(keyInfo.keyName)
			'ds:X509Data'() {
				def data = keyInfo.certificate.data
				if(data?.startsWith('-----BEGIN CERTIFICATE-----'))
					data = data.replace('-----BEGIN CERTIFICATE-----', '')
				if(data?.endsWith('-----END CERTIFICATE-----'))
					data = data.replace('-----END CERTIFICATE-----', '')
				'ds:X509Certificate'(data)
			}
		}
	}
	
	def keyDescriptor(builder, keyDescriptor) {
		if(!keyDescriptor.disabled) {
			builder.KeyDescriptor(use: keyDescriptor.keyType) {
				keyInfo(builder, keyDescriptor.keyInfo)
				if(keyDescriptor.encryptionMethod) {
					EncryptionMethod(Algorithm:keyDescriptor.encryptionMethod.algorithm) {
						if(keyDescriptor.encryptionMethod.keySize)
							'xenc:KeySize'('xmlns:xenc':'http://www.w3.org/2001/04/xmlenc#', keyDescriptor.encryptionMethod.keySize)
						if(keyDescriptor.encryptionMethod.oaeParams)
						'xenc:OAEPparams'('xmlns:xenc':'http://www.w3.org/2001/04/xmlenc#', keyDescriptor.encryptionMethod.oaeParams)
					}
				}
			}
		}
	}
	
	@Transactional(readOnly = true)
	def entitiesDescriptor(builder, all, minimal, roleExtensions, entitiesDescriptor, validUntil, certificateAuthorities) {
		
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
		
		builder.EntitiesDescriptor("xmlns":"urn:oasis:names:tc:SAML:2.0:metadata", "xmlns:xsi":"http://www.w3.org/2001/XMLSchema-instance", 'xmlns:saml':'urn:oasis:names:tc:SAML:2.0:assertion', 'xmlns:shibmd':'urn:mace:shibboleth:metadata:1.0',
			'xmlns:ds':'http://www.w3.org/2000/09/xmldsig#',
			"xsi:schemaLocation":"urn:oasis:names:tc:SAML:2.0:metadata saml-schema-metadata-2.0.xsd urn:mace:shibboleth:metadata:1.0 shibboleth-metadata-1.0.xsd http://www.w3.org/2000/09/xmldsig# xmldsig-core-schema.xsd",
			validUntil:sdf.format(validUntil), Name:entitiesDescriptor.name) {
			
			if(certificateAuthorities && certificateAuthorities.size() != 0) {
				builder.Extensions() {
					builder."shibmd:KeyAuthority"("xmlns:shibmd":"urn:mace:shibboleth:metadata:1.0", VerifyDepth: 5) {
						certificateAuthorities?.sort{it.id}.each { ca ->
							caKeyInfo(builder, ca)
						}
					}
				}
			}
			entitiesDescriptor.entitiesDescriptors?.sort{it.id}?.each { eds ->
				this.entitiesDescriptor(builder, all, minimal, roleExtensions, eds)
			}
			entitiesDescriptor.entityDescriptors?.sort{it.entityID}.each { ed ->
				entityDescriptor(builder, all, minimal, roleExtensions, ed)
			}
		}
	}
	
	@Transactional(readOnly = true)
	def entitiesDescriptor(builder, all, minimal, roleExtensions, entitiesDescriptor) {
		builder.EntitiesDescriptor() {
			entitiesDescriptor.entitiesDescriptors?.sort{it.id}?.each { eds ->
				this.entitiesDescriptor(builder, all, minimal, roleExtensions, eds)
			}
			entitiesDescriptor.entityDescriptors?.sort{it.entityID}.each { ed ->
				entityDescriptor(builder, all, minimal, roleExtensions, ed)
			}
		}
	}
	
	@Transactional(readOnly = true)
	def entityDescriptor(builder, all, minimal, roleExtensions, entityDescriptor) {
		if(all || (entityDescriptor.approved && entityDescriptor.active && entityDescriptor.organization.approved && entityDescriptor.organization.active)) {
			builder.EntityDescriptor(entityID:entityDescriptor.entityID) {
				entityDescriptor.idpDescriptors?.sort{it.id}?.each { idp -> idpSSODescriptor(builder, all, minimal, roleExtensions, idp) }
				entityDescriptor.spDescriptors?.sort{it.id}?.each { sp -> spSSODescriptor(builder, all, minimal, roleExtensions, sp) }
				entityDescriptor.attributeAuthorityDescriptors?.sort{it.id}?.each { aa -> attributeAuthorityDescriptor(builder, all, minimal, roleExtensions, aa)}

				organization(builder, entityDescriptor.organization)
				entityDescriptor.contacts?.sort{it.id}.each{cp -> contactPerson(builder, cp)}
			}
		}
	}
		
	def endpoint(builder, all, minimal, type, endpoint) {
		if(all || (endpoint.active && endpoint.approved)) {
			if(!endpoint.responseLocation)
				builder."$type"(Binding: endpoint.binding.uri, Location:endpoint.location.uri)
			else
				builder."$type"(Binding: endpoint.binding.uri, Location:endpoint.location.uri, ResponseLocation: endpoint.responseLocation.uri)
		}
	}
	
	def indexedEndpoint(builder, all, minimal, type, endpoint, index) { 
		if(all || (endpoint.active && endpoint.approved)) {
			if(!endpoint.responseLocation)
				builder."$type"(Binding: endpoint.binding.uri, Location:endpoint.location.uri, index:index, isDefault:endpoint.isDefault)
			else
				builder."$type"(Binding: endpoint.binding.uri, Location:endpoint.location.uri, ResponseLocation: endpoint.responseLocation.uri, index:index, isDefault:endpoint.isDefault)
		}
	}
	
	def samlURI(builder, type, uri) {
		builder."$type"(uri.uri)
	}
	
	def attribute(builder, attr) {
		if(attr.base.nameFormat?.uri) {
			builder.'saml:Attribute'(Name: attr.base.name, FriendlyName:attr.base.friendlyName, NameFormat:attr.base.nameFormat?.uri) {
				attr.values?.sort{it?.value}.each {
					'saml:AttributeValue'(it.value)
				}
			}
		}
		else {
			builder.'saml:Attribute'(Name: attr.base.name, FriendlyName:attr.base.friendlyName, NameFormat: 'urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified') {
				attr.values?.sort{it?.value}.each {
					'saml:AttributeValue'(it.value)
				}
			}
		}
	}
	
	def requestedAttribute(builder, all, minimal, attr) {
		if(all || attr.approved) {
			if(!attr.base.specificationRequired || (attr.base.specificationRequired && attr.values.size() > 0)) {
				if(attr.base.nameFormat?.uri) {
					builder.RequestedAttribute(Name: attr.base.name, FriendlyName:attr.base.friendlyName, isRequired:attr.isRequired, NameFormat:attr.base.nameFormat?.uri) {
						attr.values?.sort{it?.value}.each {
							'saml:AttributeValue'(it.value)
						}
					}
				} else {
					builder.RequestedAttribute(Name: attr.base.name, FriendlyName:attr.base.friendlyName, isRequired:attr.isRequired, NameFormat: 'urn:oasis:names:tc:SAML:2.0:attrname-format:unspecified') {
						attr.values?.sort{it?.value}.each {
							'saml:AttributeValue'(it.value)
						}
					}
				}
			} else {
				log.error "Not rendering $attr representing ${attr.base} because no values have been specified"
			}
		}
	}
	
	def attributeConsumingService(builder, all, minimal, acs, index) {
		if(acs.serviceNames?.size() == 0) {
			log.warn "Attribute Consuming Service with no serviceName can't be populated to metadata"
			return
		}
		if(acs.requestedAttributes?.size() == 0) {
			log.warn "Attribute Consuming Service with no requested attributes can't be populated to metadata"
			return
		}
		
		builder.AttributeConsumingService(index:index, isDefault:acs.isDefault) {
			acs.serviceNames?.sort{it}.each {
				localizedName(builder, "ServiceName", acs.lang, it)
			}
			acs.serviceDescriptions?.sort{it}.each {
				localizedName(builder, "ServiceDescription", acs.lang, it)
			}
			acs.requestedAttributes?.sort{it.base.name}.each{ attr -> requestedAttribute(builder, all, minimal, attr)}
		}
	}
	
	def roleDescriptor(builder, minimal, roleExtensions, roleDescriptor) {
		if(roleExtensions)
			"${roleDescriptor.class.name.split('\\.').last()}Extensions"(builder, roleDescriptor)
		roleDescriptor.keyDescriptors?.sort{it.id}.each{keyDescriptor(builder, it)}		
		if(!minimal) {
			organization(builder, roleDescriptor.organization)
			roleDescriptor.contacts?.sort{it.id}.each{cp -> contactPerson(builder, cp)}
		}
	}
	
	def ssoDescriptor(builder, all, minimal, ssoDescriptor) {
		ssoDescriptor.artifactResolutionServices?.sort{it.id}.eachWithIndex{ars, i -> indexedEndpoint(builder, all, minimal, "ArtifactResolutionService", ars, i+1)}
		ssoDescriptor.singleLogoutServices?.sort{it.id}.each{sls -> endpoint(builder, all, minimal, "SingleLogoutService", sls)}
		ssoDescriptor.manageNameIDServices?.sort{it.id}.each{mnids -> endpoint(builder, all, minimal, "ManageNameIDService", mnids)}
		ssoDescriptor.nameIDFormats?.sort{it.uri}.each{nidf -> samlURI(builder, "NameIDFormat", nidf)}
	}
	
	def idpSSODescriptor(builder, all, minimal, roleExtensions, idpSSODescriptor) {
		if(all || (idpSSODescriptor.approved && idpSSODescriptor.active)) {
			builder.IDPSSODescriptor(protocolSupportEnumeration: idpSSODescriptor.protocolSupportEnumerations.sort{it.uri}.collect({it.uri}).join(' ')) {
				roleDescriptor(builder, minimal, roleExtensions, idpSSODescriptor)
				ssoDescriptor(builder, all, minimal, idpSSODescriptor)
			
				idpSSODescriptor.singleSignOnServices?.sort{it.id}.each{ sso -> endpoint(builder, all, minimal, "SingleSignOnService", sso) }
				idpSSODescriptor.nameIDMappingServices?.sort{it.id}.each{ nidms -> endpoint(builder, all, minimal, "NameIDMappingService", nidms) }
				idpSSODescriptor.assertionIDRequestServices?.sort{it.id}.each{ aidrs -> endpoint(builder, all, minimal, "AssertionIDRequestService", aidrs) }
				idpSSODescriptor.attributeProfiles?.sort{it.id}.each{ ap -> samlURI(builder, "AttributeProfile", ap) }
				if(!minimal)
					idpSSODescriptor.attributes?.sort{it.base.name}.each{ attr -> attribute(builder, attr)}
			}
		}
	}
	
	def spSSODescriptor(builder, all, minimal, roleExtensions, spSSODescriptor) {
		if(all || (spSSODescriptor.active && spSSODescriptor.approved)) {
			builder.SPSSODescriptor(protocolSupportEnumeration: spSSODescriptor.protocolSupportEnumerations.sort{it.uri}.collect({it.uri}).join(' ')) {
				roleDescriptor(builder, minimal, roleExtensions, spSSODescriptor)
				ssoDescriptor(builder, all, minimal, spSSODescriptor)
			
				spSSODescriptor.assertionConsumerServices?.sort{it.id}.eachWithIndex{ ars, i -> indexedEndpoint(builder, all, minimal, "AssertionConsumerService", ars, i+1) }
				spSSODescriptor.attributeConsumingServices?.sort{it.id}.eachWithIndex{ acs, i -> 
					if(acs.serviceNames?.size() > 0 && acs.requestedAttributes?.size() > 0 )
						attributeConsumingService(builder, all, minimal, acs, i+1)
					else
						log.warn "$acs for $spSSODescriptor will not be rendered because it either has no names defined or no attributes being requested"
				}
			}
		}
	}
	
	def attributeAuthorityDescriptor(builder, all, minimal, roleExtensions, aaDescriptor) {
		if(all || (aaDescriptor.approved && aaDescriptor.active)) {		
			if(aaDescriptor.collaborator) {
				if(all || (aaDescriptor.collaborator.approved && aaDescriptor.collaborator.active)) {
					builder.AttributeAuthorityDescriptor(protocolSupportEnumeration: aaDescriptor.protocolSupportEnumerations.sort{it.uri}.collect({it.uri}).join(' ')) {
						// We don't currently provide direct AA manipulation to reduce general end user complexity.
						// So where a collaborative relationship exists we use all common data from the IDP to render the AA
						// If it isn't collaborative we'll assume manual DB intervention and render direct ;-).
						roleDescriptor(builder, minimal, roleExtensions, aaDescriptor.collaborator)	
						aaDescriptor.attributeServices?.sort{it.id}.each{ attrserv -> endpoint(builder, all, minimal, "AttributeService", attrserv) }
						aaDescriptor.collaborator.assertionIDRequestServices?.sort{it.id}.each{ aidrs -> endpoint(builder, all, minimal, "AssertionIDRequestService", aidrs) }
						aaDescriptor.collaborator.nameIDFormats?.sort{it.uri}.each{ nidf -> samlURI(builder, "NameIDFormat", nidf) }
						aaDescriptor.collaborator.attributeProfiles?.sort{it.id}.each{ ap -> samlURI(builder, "AttributeProfile", ap) }
						if(!minimal)
							aaDescriptor.collaborator.attributes?.sort{it.base.name}.each{ attr -> attribute(builder, attr) }
					}
				}
			}
			else {
				builder.AttributeAuthorityDescriptor(protocolSupportEnumeration: aaDescriptor.protocolSupportEnumerations.sort{it.uri}.collect({it.uri}).join(' ')) {
					roleDescriptor(builder, minimal, roleExtensions, aaDescriptor)	
					aaDescriptor.attributeServices?.sort{it.id}.each{ attrserv -> endpoint(builder, all, minimal, "AttributeService", attrserv) }
					aaDescriptor.assertionIDRequestServices?.sort{it.id}.each{ aidrs -> endpoint(builder, all, minimal, "AssertionIDRequestService", aidrs) }
					aaDescriptor.nameIDFormats?.sort{it.id}.each{ nidf -> samlURI(builder, "NameIDFormat", nidf) }
					aaDescriptor.attributeProfiles?.sort{it.uri}.each{ ap -> samlURI(builder, "AttributeProfile", ap) }
					aaDescriptor.attributes?.sort{it.base.name}.each{ attr -> attribute(builder, attr) }
				}
			}
		}
	}
	
	def IDPSSODescriptorExtensions(builder, roleDescriptor) {
		builder.Extensions() {
			builder."shibmd:Scope" (regexp:false, roleDescriptor.scope)
		}
	}
	
	def SPSSODescriptorExtensions(builder, spSSODescriptor) {
		if(spSSODescriptor.discoveryResponseServices) {
			builder.Extensions() {
				spSSODescriptor.discoveryResponseServices.eachWithIndex { endpoint, i ->
					builder."dsr:DiscoveryResponse"("xmlns:dsr":"urn:oasis:names:tc:SAML:profiles:SSO:idp-discovery-protocol", Binding: endpoint.binding.uri, Location:endpoint.location.uri, index:i+1, isDefault:endpoint.isDefault)
				}
			}
		}
	}
	
	def AttributeAuthorityDescriptorExtensions(builder, roleDescriptor) {
		builder.Extensions() {
			builder."shibmd:Scope" (regexp:false, roleDescriptor.scope)
		}
	}
	
}