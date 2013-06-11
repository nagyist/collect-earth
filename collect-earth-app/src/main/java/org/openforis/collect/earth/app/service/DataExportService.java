package org.openforis.collect.earth.app.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.openforis.collect.csv.AutomaticColumnProvider;
import org.openforis.collect.csv.ColumnProvider;
import org.openforis.collect.csv.ColumnProviderChain;
import org.openforis.collect.csv.DataTransformation;
import org.openforis.collect.csv.ModelCsvWriter;
import org.openforis.collect.csv.NodePositionColumnProvider;
import org.openforis.collect.csv.PivotExpressionColumnProvider;
import org.openforis.collect.csv.SingleAttributeColumnProvider;
import org.openforis.collect.manager.RecordManager;
import org.openforis.collect.model.CollectRecord;
import org.openforis.collect.model.CollectRecord.Step;
import org.openforis.collect.model.RecordSummarySortField;
import org.openforis.idm.metamodel.AttributeDefinition;
import org.openforis.idm.metamodel.EntityDefinition;
import org.openforis.idm.metamodel.NodeDefinition;
import org.openforis.idm.metamodel.Schema;
import org.openforis.idm.model.expression.InvalidExpressionException;
import org.springframework.beans.factory.annotation.Autowired;

public class DataExportService {

	@Autowired
	EarthSurveyService earthSurveyService;

	@Autowired
	RecordManager recordManager;

	private ColumnProvider createAncestorColumnProvider(EntityDefinition entityDefn, int depth) {
		List<AttributeDefinition> keyAttrDefns = entityDefn.getKeyAttributeDefinitions();
		List<ColumnProvider> providers = new ArrayList<ColumnProvider>();
		for (AttributeDefinition keyDefn : keyAttrDefns) {
			String columnName = createKeyAttributeColumnName(keyDefn);
			SingleAttributeColumnProvider keyColumnProvider = new SingleAttributeColumnProvider(keyDefn.getName(), columnName);
			providers.add(keyColumnProvider);
		}
		if (entityDefn.getParentDefinition() != null) {
			ColumnProvider positionColumnProvider = createPositionColumnProvider(entityDefn);
			providers.add(positionColumnProvider);
		}
		String expression = StringUtils.repeat("parent()", "/", depth);
		ColumnProvider result = new PivotExpressionColumnProvider(expression, providers.toArray(new ColumnProvider[1]));
		return result;
	}

	private List<ColumnProvider> createAncestorsColumnsProvider(EntityDefinition entityDefn) {
		List<ColumnProvider> columnProviders = new ArrayList<ColumnProvider>();
		EntityDefinition parentDefn = (EntityDefinition) entityDefn.getParentDefinition();
		int depth = 1;
		while (parentDefn != null) {
			ColumnProvider parentKeysColumnsProvider = createAncestorColumnProvider(parentDefn, depth);
			columnProviders.add(0, parentKeysColumnsProvider);
			parentDefn = (EntityDefinition) parentDefn.getParentDefinition();
			depth++;
		}
		return columnProviders;
	}

	private String createKeyAttributeColumnName(AttributeDefinition attrDefn) {
		StringBuilder sb = new StringBuilder();
		String name = attrDefn.getName();
		sb.append(name);
		EntityDefinition parent = (EntityDefinition) attrDefn.getParentDefinition();
		while (parent != null) {
			String parentName = parent.getName();
			sb.insert(0, '_').insert(0, parentName);
			parent = (EntityDefinition) parent.getParentDefinition();
		}
		return sb.toString();
	}

	private String createPositionColumnName(NodeDefinition nodeDefn) {
		StringBuilder sb = new StringBuilder();
		String name = nodeDefn.getName();
		sb.append(name);
		sb.append("_position");
		EntityDefinition parent = (EntityDefinition) nodeDefn.getParentDefinition();
		while (parent != null) {
			String parentName = parent.getName();
			sb.insert(0, '_').insert(0, parentName);
			parent = (EntityDefinition) parent.getParentDefinition();
		}
		return sb.toString();
	}

	private ColumnProvider createPositionColumnProvider(EntityDefinition entityDefn) {
		String columnName = createPositionColumnName(entityDefn);
		NodePositionColumnProvider columnProvider = new NodePositionColumnProvider(columnName);
		return columnProvider;
	}

	public void exportSurveyAsCsv(OutputStream exportToStream) throws IOException, InvalidExpressionException {
		Writer outputWriter = new OutputStreamWriter(exportToStream);
		DataTransformation transform = getTransform();

		ModelCsvWriter modelWriter = new ModelCsvWriter(outputWriter, transform);
		modelWriter.printColumnHeadings();

		List<CollectRecord> summaries = recordManager.loadSummaries(earthSurveyService.getCollectSurvey(),
				EarthSurveyService.ROOT_ENTITY_NAME, 0, Integer.MAX_VALUE, (List<RecordSummarySortField>) null, (String) null);

		int stepNumber = Step.ENTRY.getStepNumber();
		for (CollectRecord s : summaries) {

			if (stepNumber == s.getStep().getStepNumber()) {
				CollectRecord record = recordManager.load(earthSurveyService.getCollectSurvey(), s.getId(), stepNumber);
				modelWriter.printData(record);

			}
		}
		exportToStream.close();
	}

	private DataTransformation getTransform() throws InvalidExpressionException {
		Schema schema = earthSurveyService.getCollectSurvey().getSchema();
		EntityDefinition entityDefn = (EntityDefinition) schema.getDefinitionById(EarthSurveyService.ROOT_ENTITY_ID);
		List<ColumnProvider> columnProviders = createAncestorsColumnsProvider(entityDefn);
		columnProviders.add(new AutomaticColumnProvider(entityDefn));
		ColumnProvider provider = new ColumnProviderChain(columnProviders);
		String axisPath = entityDefn.getPath();
		return new DataTransformation(axisPath, provider);
	}

}