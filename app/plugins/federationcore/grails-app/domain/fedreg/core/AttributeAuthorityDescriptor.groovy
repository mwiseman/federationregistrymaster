/*
 *	A Grails/Hibernate compatible environment for SAML2 metadata types with application specific 
 *	data extensions as appropriate.
 * 
 *	Copyright (C) 2010 Australian Access Federation
 *
 *	Licensed under the Apache License, Version 2.0 (the "License");
 *	you may not use this file except in compliance with the License.
 *	You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software
 *	distributed under the License is distributed on an "AS IS" BASIS,
 *	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *	See the License for the specific language governing permissions and
 *	limitations under the License.
 */

package fedreg.core

/**
 * @author Bradley Beddoes
 */
class AttributeAuthorityDescriptor extends RoleDescriptor {
	static auditable = true
	
	IDPSSODescriptor collaborator    // This links the AA with an IDP that collaborates with it to provide authentication assertion services
	String scope
	
	static belongsTo = [entityDescriptor:EntityDescriptor]

	static hasMany = [
		  attributeServices: AttributeService,
		  assertionIDRequestServices: AssertionIDRequestService,
		  nameIDFormats: SamlURI,
		  attributeProfiles: String,
		  attributes: Attribute
	]

	static constraints = {
		scope(nullable:false)
		collaborator(nullable:true)
		assertionIDRequestServices(nullable: true)
		nameIDFormats(nullable: true)
		attributeProfiles(nullable: true)
		attributes(nullable: true)
	}

	public String toString() {	"attributeauthoritydescriptor:[id:$id]" }
	
	public boolean functioning() {
		if(collaborator)
			( active && approved && collaborator.functioning() && entityDescriptor.functioning() )
		else
			( active && approved && entityDescriptor.functioning() )
	}
}
