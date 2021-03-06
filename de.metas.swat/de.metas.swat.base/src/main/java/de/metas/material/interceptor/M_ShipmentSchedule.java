package de.metas.material.interceptor;

import static org.adempiere.model.InterfaceWrapperHelper.getTrxName;

import java.math.BigDecimal;
import java.sql.Timestamp;

import org.adempiere.ad.modelvalidator.InterceptorUtil;
import org.adempiere.ad.modelvalidator.ModelChangeType;
import org.adempiere.ad.modelvalidator.annotations.Interceptor;
import org.adempiere.ad.modelvalidator.annotations.ModelChange;
import org.adempiere.model.InterfaceWrapperHelper;
import org.adempiere.util.Services;
import org.compiere.Adempiere;
import org.compiere.model.ModelValidator;

import com.google.common.annotations.VisibleForTesting;

import de.metas.inoutcandidate.api.IShipmentScheduleEffectiveBL;
import de.metas.inoutcandidate.model.I_M_ShipmentSchedule;
import de.metas.material.event.MaterialEventService;
import de.metas.material.event.ModelProductDescriptorExtractor;
import de.metas.material.event.commons.EventDescriptor;
import de.metas.material.event.commons.MaterialDescriptor;
import de.metas.material.event.commons.ProductDescriptor;
import de.metas.material.event.shipmentschedule.AbstractShipmentScheduleEvent;
import de.metas.material.event.shipmentschedule.ShipmentScheduleCreatedEvent;
import de.metas.material.event.shipmentschedule.ShipmentScheduleDeletedEvent;
import de.metas.material.event.shipmentschedule.ShipmentScheduleUpdatedEvent;
import lombok.NonNull;

/**
 * Shipment Schedule module: M_ShipmentSchedule
 *
 * @author tsa
 *
 */
@Interceptor(I_M_ShipmentSchedule.class)
public class M_ShipmentSchedule
{
	static final M_ShipmentSchedule INSTANCE = new M_ShipmentSchedule();

	private M_ShipmentSchedule()
	{
	}

	@ModelChange(timings = {
			ModelValidator.TYPE_AFTER_NEW,
			ModelValidator.TYPE_AFTER_CHANGE,
			ModelValidator.TYPE_BEFORE_DELETE /* before delete because we still need the M_ShipmentSchedule_ID */
	}, ifColumnsChanged = {
			I_M_ShipmentSchedule.COLUMNNAME_QtyOrdered_Calculated,
			I_M_ShipmentSchedule.COLUMNNAME_QtyOrdered_Override,
			I_M_ShipmentSchedule.COLUMNNAME_QtyReserved,
			I_M_ShipmentSchedule.COLUMNNAME_M_Product_ID,
			I_M_ShipmentSchedule.COLUMNNAME_M_Warehouse_Override_ID,
			I_M_ShipmentSchedule.COLUMNNAME_M_Warehouse_ID,
			I_M_ShipmentSchedule.COLUMNNAME_AD_Org_ID,
			I_M_ShipmentSchedule.COLUMNNAME_PreparationDate_Override,
			I_M_ShipmentSchedule.COLUMNNAME_PreparationDate,
			I_M_ShipmentSchedule.COLUMNNAME_IsActive /* IsActive=N shall be threaded like a deletion */ })
	public void createAndFireEvent(
			@NonNull final I_M_ShipmentSchedule schedule,
			@NonNull final ModelChangeType timing)
	{
		final AbstractShipmentScheduleEvent event = createShipmentScheduleEvent(schedule, timing);

		final MaterialEventService materialEventService = Adempiere.getBean(MaterialEventService.class);
		materialEventService.fireEventAfterNextCommit(event, getTrxName(schedule));
	}

	@VisibleForTesting
	AbstractShipmentScheduleEvent createShipmentScheduleEvent(
			@NonNull final I_M_ShipmentSchedule shipmentSchedule,
			@NonNull final ModelChangeType timing)
	{
		final boolean created = timing.isNew() || InterceptorUtil.isJustActivated(shipmentSchedule);
		if (created)
		{
			return createCreatedEvent(shipmentSchedule);
		}

		final boolean deleted = timing.isDelete() || InterceptorUtil.isJustDeactivated(shipmentSchedule);
		if (deleted)
		{
			return createDeletedEvent(shipmentSchedule);
		}

		return createUpdatedEvent(shipmentSchedule);
	}

	private AbstractShipmentScheduleEvent createCreatedEvent(@NonNull final I_M_ShipmentSchedule shipmentSchedule)
	{
		final MaterialDescriptor orderedMaterial = createOrdereMaterialDescriptor(shipmentSchedule);

		final ShipmentScheduleCreatedEvent event = ShipmentScheduleCreatedEvent.builder()
				.eventDescriptor(EventDescriptor.createNew(shipmentSchedule))
				.orderedMaterial(orderedMaterial)
				.reservedQuantity(shipmentSchedule.getQtyReserved())
				.shipmentScheduleId(shipmentSchedule.getM_ShipmentSchedule_ID())
				.orderLineId(shipmentSchedule.getC_OrderLine_ID())
				.build();
		return event;
	}

	private AbstractShipmentScheduleEvent createUpdatedEvent(@NonNull final I_M_ShipmentSchedule shipmentSchedule)
	{
		final MaterialDescriptor orderedMaterial = createOrdereMaterialDescriptor(shipmentSchedule);

		final I_M_ShipmentSchedule oldShipmentSchedule = InterfaceWrapperHelper.createOld(shipmentSchedule, I_M_ShipmentSchedule.class);

		final IShipmentScheduleEffectiveBL shipmentScheduleEffectiveBL = Services.get(IShipmentScheduleEffectiveBL.class);
		final BigDecimal oldOrderedQuantity = shipmentScheduleEffectiveBL.computeQtyOrdered(oldShipmentSchedule);

		final ShipmentScheduleUpdatedEvent event = ShipmentScheduleUpdatedEvent.builder()
				.eventDescriptor(EventDescriptor.createNew(shipmentSchedule))
				.orderedMaterial(orderedMaterial)
				.reservedQuantity(shipmentSchedule.getQtyReserved())
				.shipmentScheduleId(shipmentSchedule.getM_ShipmentSchedule_ID())
				.reservedQuantityDelta(shipmentSchedule.getQtyReserved().subtract(oldShipmentSchedule.getQtyReserved()))
				.orderedQuantityDelta(orderedMaterial.getQuantity().subtract(oldOrderedQuantity))
				.build();
		return event;
	}

	private AbstractShipmentScheduleEvent createDeletedEvent(@NonNull final I_M_ShipmentSchedule shipmentSchedule)
	{
		final MaterialDescriptor orderedMaterial = createOrdereMaterialDescriptor(shipmentSchedule);

		final ShipmentScheduleDeletedEvent event = ShipmentScheduleDeletedEvent.builder()
				.eventDescriptor(EventDescriptor.createNew(shipmentSchedule))
				.orderedMaterial(orderedMaterial)
				.reservedQuantity(shipmentSchedule.getQtyReserved())
				.shipmentScheduleId(shipmentSchedule.getM_ShipmentSchedule_ID())
				.build();
		return event;
	}

	private MaterialDescriptor createOrdereMaterialDescriptor(@NonNull final I_M_ShipmentSchedule shipmentSchedule)
	{
		final IShipmentScheduleEffectiveBL shipmentScheduleEffectiveBL = Services.get(IShipmentScheduleEffectiveBL.class);
		final BigDecimal orderedQuantity = shipmentScheduleEffectiveBL.computeQtyOrdered(shipmentSchedule);

		final Timestamp preparationDate = shipmentScheduleEffectiveBL.getPreparationDate(shipmentSchedule);

		final ModelProductDescriptorExtractor productDescriptorFactory = Adempiere.getBean(ModelProductDescriptorExtractor.class);
		final ProductDescriptor productDescriptor = productDescriptorFactory.createProductDescriptor(shipmentSchedule);

		final MaterialDescriptor orderedMaterial = MaterialDescriptor.builder()
				.date(preparationDate)
				.productDescriptor(productDescriptor)
				.warehouseId(shipmentScheduleEffectiveBL.getWarehouseId(shipmentSchedule))
				.bPartnerId(shipmentScheduleEffectiveBL.getC_BPartner_ID(shipmentSchedule))
				.quantity(orderedQuantity)
				.build();
		return orderedMaterial;
	}

	private BigDecimal computeEffectiveOrderedQuantity(
			@NonNull final I_M_ShipmentSchedule schedule,
			@NonNull final ModelChangeType timing)
	{
		final BigDecimal quantity;
		final boolean deleted = timing.isDelete() || !schedule.isActive();
		if (deleted)
		{
			quantity = BigDecimal.ZERO;
		}
		else
		{
			final IShipmentScheduleEffectiveBL shipmentScheduleEffectiveBL = Services.get(IShipmentScheduleEffectiveBL.class);
			quantity = schedule.getQtyDelivered()
					.max(schedule.getQtyToDeliver())
					.max(shipmentScheduleEffectiveBL.computeQtyOrdered(schedule));
		}
		return quantity;
	}
}
