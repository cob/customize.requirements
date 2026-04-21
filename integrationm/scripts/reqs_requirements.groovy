import groovy.transform.Field

@Field DEFINITION = "Requirements"

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