import config.EnvironmentConfig
import groovy.text.SimpleTemplateEngine
import groovy.transform.Field;
import utils.MarkdownProcessor;

@Field DEFINITION = "Requirements"
@Field FROM = "CoB <support@cob.pt>";

if( msg.product == "recordm" && msg.type == DEFINITION ) {

	if( (msg.action == 'update' || msg.action == 'add') && msg.user != "integrationm" ) {
		def updatesReq = []
		def timestamp = msg._timestamp_
		def complianceValue = ""
		def risk = ""
		def riskValue = ""

		// get req results -> deve existir sempre 1, results são criados quando se activa um ciclo
		def activeResultQuery = "active.raw:Yes requirement.raw:${msg.id}"

		if(msg.field("Compliance").changed()){
			// update hidden value fields based on selected option
			def compliance = msg.value("Compliance") ?: ""

			switch (compliance) {
				case "Cumpre":
					complianceValue = 5
					risk = "Baixo"
					riskValue = 1
					break
				case "Não Cumpre":
					complianceValue = 1
					risk = "Alto"
					riskValue = 3
					break
				case "Cumpre Parcialmente":
					complianceValue = 3
					risk = "Médio"
					riskValue = "2"
					break
				default:
					break
			}

			updatesReq = [
				"Compliance Value" : complianceValue ?: "",
				"Risco" : risk ?: "",
				"Valor Risco" : riskValue ?: "", 
			]

			recordm.update("Requirement", msg.id, updatesReq)
		}

		if(msg.field('Evidence').changed() && msg.values('Evidence').size() > 0) {
			def parentIds = msg.value('Requirement Node Complete Id')
								.split(/\./)
								.collect { it.trim() }
								.findAll { it }
								.join(' OR ')
								
			def questionnaire = recordm.search('Requirement Nodes', "type.raw:\"Questionário\" id:(${parentIds})") 
									   .getHits()[0]
									   
			def evidenceIds = msg.values('Evidence').join(' OR ')
			recordm.update('Requirements Evidences', "id:(${evidenceIds})", ['Requirement Node' : questionnaire.id])
		}


		// update requirement results
		def updates = mapForResult(msg)
		recordm.update("Requirement Results", activeResultQuery, updates)

        // $log will posteriorly "delete" New Comment and move it to Comments
        if(msg.field("New Comment").changed() && msg.value("New Comment") != null) {
            def identifier = msg.value("Identifier")
            def title = msg.value("Title")
            def state = msg.value("Answer State")
            def lastComment = msg.value("New Comment") ?: ""
            def clientEmail = msg.value("Answer Responsible Email")
            def responsibleEmail = msg.value("Acceptance Responsible Email")
            def targetEmails = [clientEmail, responsibleEmail]

            String body = buildMsgBody(msg.instance.id, identifier, title, state, lastComment)
            String subj = "Requirement '" + identifier + " - " + title + "' was " + state

            if (targetEmails.size() > 0) {
                sendEmails(FROM, targetEmails, subj, body)
            } else {
                log.info("Req email not sent. No target emails defined")
            }
        }
	}
}

def mapForResult(msg){
    def result = [
        "Requirement" : msg.id,
        "Answer State" : msg.value("Answer State") ?: "",
        "Importance" : msg.value("Importance") ?: "", 
        "Aplicable" : msg.value("Aplicable") ?: "", 
        "Compliance" : msg.value("Compliance") ?: "", 
        "Compliance Value" : msg.value("Compliance Value") ?: "",
        "Risk" : msg.value("Risk") ?: "",
        "Risk Value" : msg.value("Risk Value") ?: "",
    ]
    
    // Add all evidence references with indexed fields
    def evidences = msg.values("Evidence") ?: []
    evidences.eachWithIndex { id, idx ->
        result["Evidence[${idx}]"] = id
    }
    
    return result
}


def getResultOfCycle(Long timestamp, Integer reqId) {
	def calendar = Calendar.getInstance()
	calendar.setTimeInMillis(timestamp)
	calendar.setTimeZone(TimeZone.getTimeZone("Europe/Lisbon"))

	def year = calendar.get(Calendar.YEAR)
	def hits = recordm.search("Requirement Results",
						"requirement.raw:$reqId year.raw:$year",
						[size: 1]).getHits()
	if( hits.size() > 0) {
		return hits[0].id
	} else {
		return null
	}

}

def sendEmails(from, emails, subject, body) {
    if (emails != null && emails.size > 0) {
        if (EnvironmentConfig.EMAIL_ENABLED) {
            from = new String(from.getBytes(), "ISO-8859-1")
            email.send(subject, body, ["from": from.toString(), "to": emails, "html": true])
        } else {
            log.info("would email $emails");
        }
    }
}

def buildMsgBody(instanceId, reqIdentifier, reqTitle, reqStatus, lastComment) {

    def body = new SimpleTemplateEngine().createTemplate(
            '''
Requirement <strong>"${req_identifier} - ${req_title}"</strong> has been marked <strong>${req_status}</strong>.
<br><br>
<div style="background-color:#f6f6f6;padding:10px">${lastComment}</div>
<br><br>
You can check it <a href="https://suporte.cultofbits.com/recordm/index.html#/instance/${id}">here</a>.<br>
'''
    ).make([
            req_identifier: reqIdentifier,
            req_title     : reqTitle,
            req_status    : reqStatus,
            lastComment   : new MarkdownProcessor().toHtml(lastComment),
            id            : instanceId
    ]).toString()

    return body
}