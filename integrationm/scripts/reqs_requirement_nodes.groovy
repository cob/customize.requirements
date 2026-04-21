import groovy.transform.Field

@Field DEFINITION = "Requirement Nodes"

if( msg.product == "recordm" && msg.type == DEFINITION ) {

	if ( msg.action == 'add' && msg.value('Template') != null) {

		def template = recordm.get(msg.value("Template")).body 

		def userTag = msg.value('Tag') ?: null

		def idMapping = [:]
		idMapping[template.id] = msg.id

		if (userTag) {

			// --- Phase 1: Find all needed node IDs from matching requirements ---
			def neededNodeIds = [] as Set
			def reqQuery = "requirement_node_complete_id:\"${template.id}\" tag:${userTag}"

			recordm.stream("Requirements", reqQuery, [sort:'nível:asc'], { r ->
				def completeId = r.value('Requirement Node Complete Id') ?: ""
				completeId.findAll(/\d+/).each { id ->
					neededNodeIds.add(Integer.parseInt(id))
				}
			})

			// --- Phase 2: Create only needed nodes ---
			recordm.stream("Requirement Nodes", "parent_node_complete_id:${template.id} -id:${template.id}", [sort:'level:asc'], { r ->
				if (!neededNodeIds.contains(r.id)) {
					return
				}

				def originalParent = Integer.parseInt(r.value("Parent Node"))

				def q = "name.raw:\"${r.value('Nome')}\" complete_id.raw:${msg.id}"
				def node = getOrCreateNode(q, [
					'Name': r.value('Name'),
					'Order': r.value('Order') ?: "",
					'Parent Node': idMapping[originalParent] ?: msg.id,
					'Type' : 'Questionário-Item'
				])

				idMapping[r.id] = node.id
			})

			// --- Phase 3: Create matching requirements ---
			recordm.stream("Requirements", reqQuery, [sort:'nível:asc'], { r ->
				def parentNodeId = Integer.parseInt(r.value('Requirement Node'))
				if (!idMapping.containsKey(parentNodeId)) {
					return
				}

				def newReq = recordm.create("Requirements", [
					'Requirement Node': idMapping[parentNodeId],
					'Identifier': r.value('Identifier') ?: "",
					'Title': r.value('Title') ?: "",
					'Description': r.value('Description') ?: " ",
				]).body

				recordm.create('Requirement Results', mapForResult(newReq, true))
			})

		} else {
			// --- No tag filtering: original behaviour ---
			recordm.stream("Requirement Nodes", "parent_node_complete_id:${template.id} -id:${template.id}", [sort:'nível:asc'], { r ->
				def originalParent = Integer.parseInt(r.value("Parent Node"))

				def q = "name.raw:\"${r.value('Nome')}\" complete_id.raw:${msg.id}"
				def node = getOrCreateNode(q, [
					'Name': r.value('Name'),
					'Order': r.value('Order') ?: "",
					'Parent Node': idMapping[originalParent] ?: msg.id
				])
				idMapping[r.id] = node.id
			})

			def reqQuery = "requirement_node_complete_id:\"${template.id}\""
			recordm.stream("Requirements", reqQuery, [sort:'nível:asc'], { r ->
				def newReq = recordm.create("Requirements", [
					'Requirement Node': idMapping[Integer.parseInt(r.value('Requirement Node'))],
					'Identifier': r.value('Identifier') ?: "",
					'Title': r.value('Title') ?: "",
					'Description': r.value('Description') ?: " ",
				]).body

				recordm.create('Requirement Results', mapForResult(newReq, true))
			})
		}

		recordm.update(DEFINITION, msg.id, ["Round Start Date": msg._timestamp_])

	} else if(msg.action == 'update' && msg.field('Close Round').changedTo('Yes') ) {
		def updates = [
			'Close Round': '',
			'Round Start Date': msg._timestamp_
		]

		recordm.update(DEFINITION, msg.id, updates)

		def reqQuery = "requirement_node_complete_id:\"${msg.id}\""
		recordm.stream('Requirements', reqQuery, { req ->

			recordm.update('Requirement Results',
				"active.raw:Yes requirement.raw:${req.id}",
				['Active': 'No'])

			recordm.create('Requirement Results',
				mapForResult(req, false))

			recordm.update('Requirements', req.id,
				["Answer State": "To Review"])
		})
	}
}

def getOrCreateNode(query, node) {
	def hits = recordm.search("Requirement Nodes", query).getHits()
	if(hits.size() == 0) {
		return recordm.create("Requirement Nodes", node).body
	} else {
		return hits[0]
	}
}

def mapForResult(req, isNew) {
	return [
		"Requirement": req.id,
		"Active": "Yes",
		"Answer State": isNew ? "To Answer" : "To Review",
		"Date": msg._timestamp_,
		"Importance": req.value("Importance") ?: "",
		"Score": req.value("Score") ?: "",
	]
}