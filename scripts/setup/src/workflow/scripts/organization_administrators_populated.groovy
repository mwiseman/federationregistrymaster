import grails.plugins.nimble.core.*

workflowTaskService = ctx.getBean("workflowTaskService")

def orgAdminRole = Role.findWhere(name:"organization-${env.organization}-administrators")

if(orgAdminRole) {
	if(orgAdminRole.users?.size() > 0) {
		workflowTaskService.complete(env.taskInstanceID.toLong(), 'organization_hasadministrators')
		return
	}
}

workflowTaskService.complete(env.taskInstanceID.toLong(), 'organization_noadministrators')