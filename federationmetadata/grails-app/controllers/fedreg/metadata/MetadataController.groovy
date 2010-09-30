package fedreg.metadata

import groovy.xml.MarkupBuilder
import fedreg.core.*

class MetadataController {
	def metadataGenerationService
	def grailsApplication
	
	def current = {
		def xml = currentPublishedMetadata()
		render(text:xml, contentType:"text/xml", encoding:"UTF-8")
	}
	
	def all = {
		def xml = allMetadata()
		render(text:xml, contentType:"text/xml", encoding:"UTF-8")
	}
	
	def view = {
		def md = currentPublishedMetadata()
		[md:md]
	}
	
	def viewall = {
		def md = allMetadata()
		[md:md]
	}
	
	def currentPublishedMetadata() {
		def now = new Date();
		def validUntil = now + grailsApplication.config.fedreg.metadata.current.validForDays
		def cacheDuration = now + grailsApplication.config.fedreg.metadata.current.cacheForDays
		def federation = grailsApplication.config.fedreg.metadata.federation
		def certificateAuthorities = CAKeyInfo.list()
		
		def writer = new StringWriter()
		def builder = new MarkupBuilder(writer)
		
		def entitiesDescriptor = new EntitiesDescriptor(name:federation)
		entitiesDescriptor.entityDescriptors = EntityDescriptor.list()
		
		metadataGenerationService.entitiesDescriptor(builder, false, entitiesDescriptor, validUntil, cacheDuration, certificateAuthorities)
		writer.toString()
	}
	
	def allMetadata() {
		def now = new Date();
		def validUntil = now + grailsApplication.config.fedreg.metadata.all.validForDays
		def cacheDuration = now + grailsApplication.config.fedreg.metadata.all.cacheForDays
		def federation = grailsApplication.config.fedreg.metadata.federation
		def certificateAuthorities = CAKeyInfo.list()
		
		def writer = new StringWriter()
		def builder = new MarkupBuilder(writer)
		
		def entitiesDescriptor = new EntitiesDescriptor(name:federation)
		entitiesDescriptor.entityDescriptors = EntityDescriptor.list()
		
		metadataGenerationService.entitiesDescriptor(builder, true, entitiesDescriptor, validUntil, cacheDuration, certificateAuthorities)
		writer.toString()
	}
		
}