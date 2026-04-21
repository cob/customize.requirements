import groovy.json.JsonSlurper

def questionarios = recordm.search('Requirement Nodes', "type.raw:\"Questionário\" ${args.query ?: ''}",
					[size: 1000, runAs: args.user])
					.getHits()

def queryString = """
{
  "query": {
    "query_string": {
      "query": "active.raw:Yes",
      "default_operator": "AND"
    }
  },
  "size": 0,
  "aggs": {
    "by_questionnaire": {
      "terms": {
        "field": "requirement_node_complete_id.raw",
        "size": 1000
      },
      "aggs": {
        "answered": {
          "filter": {
            "term": {
              "answer_state.raw": "Answered"
            }
          }
        },
		"scoreGt0": {
          "filter": {
            "bool": {
              "must": {
			  "range" : {
          		  "compliance_value" : { "gte" : 0 }
      			}
			  }
            }
          }
        },
        "not_accepted": {
          "filter": {
            "term": {
              "answer_state.raw": "Not Accepted"
            }
          }
        },
        "to_answer": {
          "filter": {
            "term": {
              "answer_state.raw": "To Answer"
            }
          }
        },
        "accepted": {
          "filter": {
            "term": {
              "answer_state.raw": "Accepted"
            }
          }
        },
        "compliance_sum": {
          "sum": {
            "field": "compliance_value.raw"
          }
        }
      }
    }
  }
}
"""

def slurper = new JsonSlurper()
def relativeUrl = "/recordm/definitions/search/advanced/118"

def query = slurper.parseText(queryString)
def searchResult = actionPacks.rmRest.post(relativeUrl, query, null)
def aggResult = slurper.parseText(searchResult)

def active = questionarios.collect { q ->

	def buckets  = aggResult['aggregations']['sterms#by_questionnaire'].buckets.findAll { it.key.contains(q.id + '') }
	def total       = buckets.sum { it.doc_count }                    ?: 0
	def answered    = buckets.sum { it['filter#answered']?.doc_count    ?: 0 }  ?: 0
	def notAccepted = buckets.sum { it['filter#not_accepted']?.doc_count ?: 0 } ?: 0
	def toAnswer    = buckets.sum { it['filter#to_answer']?.doc_count   ?: 0 }  ?: 0
	def accepted    = buckets.sum { it['filter#accepted']?.doc_count    ?: 0 }  ?: 0
	def scoreSum    = buckets.sum { it['sum#compliance_sum']?.value ?: 0 } ?: 0
	def totalWScore = buckets.sum { it['filter#scoreGt0']?.doc_count    ?: 0 }  ?: 1
	def score       = (scoreSum / totalWScore).doubleValue().round(3)
	// score vazio/0 não entra para as contas de média 

	def zeroOut = total == 0

	[
		nome : q.value('Complete Name'),
		id   : q.id,
		answered : [
			total: answered,
			percentage: total == 0 ? 0 : (answered / total) * 100
		],
		toAnswer : [
			total: toAnswer,
			percentage: total == 0 ? 0 : (toAnswer / total) * 100
		],
		accepted : [
			total: accepted,
			percentage: total == 0 ? 0 : (accepted / total) * 100
		],
		notAccepted : [
			total: notAccepted,
			percentage: total == 0 ? 0 : (accepted / total) * 100
		],
		total : total,
		score: score,
		percentage : zeroOut ? 0 : (accepted / total) * 100.0,
		date: q.value('Round Start Date')
	]
}

active.sort{ !it.date }

return json(200, [data: active])
