cob.custom.customize.push(function (core, utils, ui) { 

    const DEFINITION = "Requirement Nodes";

	// Adiciona tipo e area nas checklists
    core.customizeInstances(DEFINITION, async function (instance, presenter) { 

		function updateName() {
			const template = presenter.findFieldPs('Template Name')[0].getValue()
			const nomeF = presenter.findFieldPs('Name')[0]
			
			if(template != undefined) {
				nomeF.setValue( template )
				nomeF.disable()
			} else {
				nomeF.setValue( "" )
				nomeF.enable()
			}
		}

		presenter.onFieldChange('Template Name', updateName)

		function hideTagShowSelectors(){
			const template = presenter.findFieldPs('Template Name')[0].getValue()
			if(template != "Checklist DataCenter"){
				return
			}

			var tagLabel = $('label[name="Tag"]');
			if (!tagLabel.length) return;
			
			var tagField = tagLabel.closest('li');
			var tagInput = tagField.find('.field-value');
			
			//tagField.hide();
			
			var tipoOptions = '<option value=""></option><option value="A">A</option><option value="B">B</option><option value="C">C</option><option value="D">D</option><option value="E">E</option>';
			var areaOptions = '<option value=""></option><option value="A">A</option><option value="N">N</option>';
			
			var tipoHtml = '<li class="custom-tag-selector-tipo">' +
				'<table class="instance.service.field" width="100%"><tbody>' +
				'<tr class="cob-field">' +
				'<td width="70px" class="cob-field-container-actions"></td>' +
				'<td width="160px" style="vertical-align:middle" class="cob-field-container-label">' +
				'<label>Tipo</label></td>' +
				'<td width="370px" style="vertical-align:middle" class="cob-field-container-value">' +
				'<div class="inline"><select id="custom-tipo" class="field-value">' + tipoOptions + '</select></div></td>' +
				'<td style="vertical-align:middle" class="cob-field-container-description"></td>' +
				'</tr></tbody></table></li>';
			
			var areaHtml = '<li class="custom-tag-selector-area">' +
				'<table class="instance.service.field" width="100%"><tbody>' +
				'<tr class="cob-field">' +
				'<td width="70px" class="cob-field-container-actions"></td>' +
				'<td width="160px" style="vertical-align:middle" class="cob-field-container-label">' +
				'<label>Área</label></td>' +
				'<td width="370px" style="vertical-align:middle" class="cob-field-container-value">' +
				'<div class="inline"><select id="custom-area" class="field-value">' + areaOptions + '</select></div></td>' +
				'<td style="vertical-align:middle" class="cob-field-container-description"></td>' +
				'</tr></tbody></table></li>';
			
			tagField.after(areaHtml);
			tagField.after(tipoHtml);
			
			function updateTag() {
				var tipo = $('#custom-tipo').val();
				var area = $('#custom-area').val();
				var tag = (tipo && area) ? tipo + area : '';
				
				tagInput.val(tag).trigger('change');
			}
			
			$('#custom-tipo, #custom-area').on('change', updateTag);
			
			var existingTag = tagInput.val();
			if (existingTag && existingTag.length === 2) {
				$('#custom-tipo').val(existingTag.charAt(0));
				$('#custom-area').val(existingTag.charAt(1));
			}
		}

		presenter.onFieldChange('Template Name', hideTagShowSelectors)
	})
        
	// Alterações visuais consoante arvore
    core.customizeInstances(DEFINITION, async function (instance, presenter) { 

		presenter.findFieldPs('Parent Node')[0]
			.content()[0]
			.classList.add('custom-hide')

		const tipo = (tipo) => 
			presenter.findFieldPs('Type')[0] != undefined &&
			presenter.findFieldPs('Type')[0].getValue() == tipo

		function hideOperacaoRenamePrincipais(
			condition, groupName 
		) {

			if( !condition() ) {
				return
			}

			presenter.findFieldPs('Questionnaire Data')[0]
					.content()[0]
					.classList.add('custom-hide')

			presenter.findFieldPs('Main Data')[0]
					.content()[0]
					.querySelector('.group-name')
					.innerHTML = groupName

		}

		const fornecedor = () => {
			const condition = () => tipo('Fornecedor')
			hideOperacaoRenamePrincipais(condition, 'Dados do Fornecedor')
		}

		const regional = () => {
			const condition = () => tipo('Distrito')
			hideOperacaoRenamePrincipais(condition, 'Dados da Regional')
		}

		const loja = () => {
			const condition = () => tipo('Loja')
			hideOperacaoRenamePrincipais(condition, 'Dados da Loja')
		}

		const entrepostos = () => {
			const condition = () => tipo('Entreposto')
			hideOperacaoRenamePrincipais(condition, 'Dados do Entreposto')
		}

		const templates = () => {
			const condition = () => tipo('Template')
			hideOperacaoRenamePrincipais(condition, 'Dados do Template')
		}

		const questionarios = () => {
			if( tipo('Questionário') ) {


				presenter.findFieldPs('Main Data')[0]
					.content()[0]
					.querySelector('.group-name')
					.innerHTML = "Dados do Âmbito"

				const nomeF = presenter.findFieldPs('Name')[0]
				nomeF.disable()
				nomeF.content()[0]
					.querySelector('.cob-field-container-description')
					.innerHTML = "Preenchido pelo template"

			}
		}


		[fornecedor, regional, loja, entrepostos, templates, questionarios]
			.forEach( callBack => {
				callBack()
				presenter.onFieldChange('Level', callBack)
				presenter.onFieldChange('Level 1', callBack)
			})
	})
})