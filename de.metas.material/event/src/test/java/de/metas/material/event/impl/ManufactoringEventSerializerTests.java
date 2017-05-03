package de.metas.material.event.impl;

import static org.hamcrest.Matchers.comparesEqualTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.test.AdempiereTestHelper;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.adempiere.util.time.SystemTime;
import org.compiere.model.I_AD_Table;
import org.junit.Before;
import org.junit.Test;

import de.metas.material.event.MaterialDescriptor;
import de.metas.material.event.MaterialEvent;
import de.metas.material.event.ProductionOrderRequested;
import de.metas.material.event.ProductionPlanEvent;
import de.metas.material.event.ReceiptScheduleEvent;
import de.metas.material.event.TransactionEvent;
import de.metas.material.event.pporder.PPOrder;
import de.metas.material.event.pporder.PPOrderLine;

/*
 * #%L
 * metasfresh-manufacturing-event-api
 * %%
 * Copyright (C) 2017 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

public class ManufactoringEventSerializerTests
{
	@Before
	public void init()
	{
		AdempiereTestHelper.get().init();
	}

	@Test
	public void testReceiptScheduleEvent()
	{
		final I_AD_Table someOtherTable = InterfaceWrapperHelper.newInstance(I_AD_Table.class);
		someOtherTable.setTableName("someOtherTable");
		InterfaceWrapperHelper.save(someOtherTable);

		final ReceiptScheduleEvent evt = ReceiptScheduleEvent.builder()
				.materialDescr(MaterialDescriptor.builder()
						.date(SystemTime.asDate())
						.orgId(10)
						.productId(13)
						.warehouseId(15)
						.orgId(67)
						.qty(BigDecimal.TEN)
						.build())
				.receiptScheduleDeleted(false)
				.reference(TableRecordReference.of("someOtherTable", 100))
				.when(Instant.now())
				.build();
		assertThat(evt.getMaterialDescr().getQty(), comparesEqualTo(BigDecimal.TEN)); // guard

		final String serializedEvt = MaterialEventSerializer.get().serialize(evt);
		final MaterialEvent deserializedEvt = MaterialEventSerializer.get().deserialize(serializedEvt);

		assertThat(deserializedEvt, is(evt));
	}

	@Test
	public void testTransactionEvent()
	{

		final TransactionEvent evt = createSampleTransactionEvent();

		final String serializedEvt = MaterialEventSerializer.get().serialize(evt);

		final MaterialEvent deserializedEvt = MaterialEventSerializer.get().deserialize(serializedEvt);
		assertThat(deserializedEvt instanceof TransactionEvent, is(true));
		assertThat(((TransactionEvent)deserializedEvt)
				.getMaterialDescr()
				.getProductId(), is(14)); // "spot check": picking the productId
		assertThat(deserializedEvt, is(evt));
	}

	public static TransactionEvent createSampleTransactionEvent()
	{
		final I_AD_Table someTable = InterfaceWrapperHelper.newInstance(I_AD_Table.class);
		someTable.setTableName("someTable");
		InterfaceWrapperHelper.save(someTable);

		final TransactionEvent evt = TransactionEvent
				.builder()
				.materialDescr(MaterialDescriptor.builder()
						.productId(14)
						.qty(BigDecimal.TEN)
						.date(SystemTime.asDate())
						.warehouseId(12)
						.orgId(66)
						.build())
				.reference(TableRecordReference.of(1, 2))
				.when(Instant.now())
				.build();
		return evt;
	}

	@Test
	public void testProductionOrderEvent()
	{
		final ProductionOrderRequested event = ProductionOrderRequested.builder()
				.when(Instant.now())
				.reference(TableRecordReference.of("table", 24))
				.ppOrder(PPOrder.builder()
						.datePromised(SystemTime.asDate())
						.dateStartSchedule(SystemTime.asDate())
						.orgId(100)
						.plantId(110)
						.productId(120)
						.productPlanningId(130)
						.quantity(BigDecimal.TEN)
						.uomId(140)
						.warehouseId(150)
						.warehouseId(160)
						.line(PPOrderLine.builder()
								.attributeSetInstanceId(270)
								.description("desc1")
								.productBomLineId(280)
								.productId(290)
								.qtyRequired(BigDecimal.valueOf(220))
								.receipt(true)
								.build())
						.line(PPOrderLine.builder()
								.attributeSetInstanceId(370)
								.description("desc2")
								.productBomLineId(380)
								.productId(390)
								.qtyRequired(BigDecimal.valueOf(320))
								.receipt(false)
								.build())
						.build())
				.build();

		final String serializedEvt = MaterialEventSerializer.get().serialize(event);

		final MaterialEvent deserializedEvt = MaterialEventSerializer.get().deserialize(serializedEvt);
		
		assertThat(deserializedEvt, is(event));
	}
	
	@Test
	public void testProductionPlanEvent()
	{
		final ProductionPlanEvent event = ProductionPlanEvent.builder()
				.when(Instant.now())
				.reference(TableRecordReference.of("table", 24))
				.ppOrder(PPOrder.builder()
						.datePromised(SystemTime.asDate())
						.dateStartSchedule(SystemTime.asDate())
						.orgId(100)
						.plantId(110)
						.productId(120)
						.productPlanningId(130)
						.quantity(BigDecimal.TEN)
						.uomId(140)
						.warehouseId(150)
						.warehouseId(160)
						.line(PPOrderLine.builder()
								.attributeSetInstanceId(270)
								.description("desc1")
								.productBomLineId(280)
								.productId(290)
								.qtyRequired(BigDecimal.valueOf(220))
								.receipt(true)
								.build())
						.line(PPOrderLine.builder()
								.attributeSetInstanceId(370)
								.description("desc2")
								.productBomLineId(380)
								.productId(390)
								.qtyRequired(BigDecimal.valueOf(320))
								.receipt(false)
								.build())
						.build())
				.build();

		final String serializedEvt = MaterialEventSerializer.get().serialize(event);

		final MaterialEvent deserializedEvt = MaterialEventSerializer.get().deserialize(serializedEvt);
		
		assertThat(deserializedEvt, is(event));
	}
}