/*
 *	Nimble, an extensive application base for Grails
 *	Copyright (C) 2010 Bradley Beddoes
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
import grails.plugins.nimble.core.AdminsService
import grails.plugins.nimble.core.UserService
import fedreg.core.EntityDescriptor

/**
 * Filter that works with Nimble security model to protect controllers, actions, views for Federation Registry
 *
 * @author Bradley Beddoes
 */
public class SecurityFilters extends grails.plugins.nimble.security.NimbleFilterBase {

	def grailsApplication

	def filters = {
	
		// Undertake bootstrap
		all(controller: '*') {
			before = {
				if( !['initialBootstrap','console'].contains(controllerName) && grailsApplication.config.fedreg.bootstrap)
					redirect (controller: "initialBootstrap")
			}
		}
		
		// Invitations
		invitations(controller: "invitation") {
			before = {
				accessControl (auth: false) {
					role(UserService.USER_ROLE)
				}
			}
		}

		// Dashboard
		dashboard(controller: "dashboard") {
			before = {
				accessControl (auth: false) {
					role(UserService.USER_ROLE)
				}
			}
		}

		// Members
		members(controller: "(organization|entityDescriptor|IDPSSODescriptor|SPSSODescriptor|contacts)") {
			before = {
				accessControl (auth: false) {
					role(UserService.USER_ROLE)
				}
			}
		}

		// Members Backend
		membersbackend(controller: "(attributeConsumingSerivce|descriptorAdministration|descriptorAttribute|descriptorContact|descriptorEndpoint|descriptorNameIDFormat|organizationAdministration|organizationContact|roleDescriptorCrypto|roleDescriptorMonitor)") {
			before = {
				accessControl (auth: false) {
					role(UserService.USER_ROLE)
				}
			}
		}
		
		// Service Categories
		servicecategories(controller: "serviceCategory", action:"(list|add|remove)") {
			before = {
				accessControl {
					role(UserService.USER_ROLE)
				}
			}
		}
		
		// Compliance
		compliance(controller: "(IDPSSODescriptorAttributeCompliance|attributeRelease|certifyingAuthorityUsage)") {
			before = {
				accessControl (auth: false) {
					role(UserService.USER_ROLE)
				}
			}
		}

		// Workflow
		workflow(controller: "workflow*") {
			before = {
				accessControl {
					role(UserService.USER_ROLE)
				}
			}
		}

		// Metadata
		metadata(controller: "metadata", action:"(view|viewall)") {
			before = {
				accessControl {
					role(UserService.USER_ROLE)
				}
			}
		}

		// Monitoring functionality
		monitoring(controller: "monitor") {
			before = {
				accessControl {
					role(AdminsService.ADMIN_ROLE)
				}
			}
		}

		// Administrative components
		administration(controller: "(admins|user|group|role)") {
			before = {
				accessControl {
					role(AdminsService.ADMIN_ROLE)
				}
			}
		}

		// Console and initial bootstrap
		console(controller: "(code|console|initialBootstrap)") {
			before = {
				if( ['initialBootstrap'].contains(controllerName) && !grailsApplication.config.fedreg.bootstrap)
					redirect (controller: "dashboard")
				else {	
					if(!grailsApplication.config.fedreg.bootstrap) {
						accessControl {
							role(AdminsService.ADMIN_ROLE)
						}
					}
				}
			}
		}

	}

}