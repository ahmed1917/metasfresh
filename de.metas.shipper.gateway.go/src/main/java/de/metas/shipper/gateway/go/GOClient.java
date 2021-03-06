package de.metas.shipper.gateway.go;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import javax.xml.bind.JAXBElement;

import org.adempiere.util.Check;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.oxm.Marshaller;
import org.springframework.oxm.jaxb.Jaxb2Marshaller;
import org.springframework.ws.client.core.support.WebServiceGatewaySupport;
import org.springframework.ws.transport.WebServiceMessageSender;
import org.springframework.xml.transform.StringResult;

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;

import de.metas.shipper.gateway.api.ShipperGatewayClient;
import de.metas.shipper.gateway.api.exceptions.ShipperErrorMessage;
import de.metas.shipper.gateway.api.exceptions.ShipperGatewayException;
import de.metas.shipper.gateway.api.model.Address;
import de.metas.shipper.gateway.api.model.ContactPerson;
import de.metas.shipper.gateway.api.model.DeliveryDate;
import de.metas.shipper.gateway.api.model.DeliveryOrder;
import de.metas.shipper.gateway.api.model.DeliveryPosition;
import de.metas.shipper.gateway.api.model.HWBNumber;
import de.metas.shipper.gateway.api.model.OrderId;
import de.metas.shipper.gateway.api.model.PackageDimensions;
import de.metas.shipper.gateway.api.model.PackageLabel;
import de.metas.shipper.gateway.api.model.PackageLabels;
import de.metas.shipper.gateway.api.model.PhoneNumber;
import de.metas.shipper.gateway.api.model.PickupDate;
import de.metas.shipper.gateway.go.schema.Fehlerbehandlung;
import de.metas.shipper.gateway.go.schema.GOOrderStatus;
import de.metas.shipper.gateway.go.schema.GOPackageLabelType;
import de.metas.shipper.gateway.go.schema.Label;
import de.metas.shipper.gateway.go.schema.Label.Sendung.PDFs;
import de.metas.shipper.gateway.go.schema.ObjectFactory;
import de.metas.shipper.gateway.go.schema.Sendung;
import de.metas.shipper.gateway.go.schema.Sendung.Abholadresse;
import de.metas.shipper.gateway.go.schema.Sendung.Abholdatum;
import de.metas.shipper.gateway.go.schema.Sendung.Empfaenger;
import de.metas.shipper.gateway.go.schema.Sendung.Empfaenger.Ansprechpartner;
import de.metas.shipper.gateway.go.schema.Sendung.Empfaenger.Ansprechpartner.Telefon;
import de.metas.shipper.gateway.go.schema.Sendung.SendungsPosition;
import de.metas.shipper.gateway.go.schema.Sendung.SendungsPosition.Abmessungen;
import de.metas.shipper.gateway.go.schema.Sendung.Zustelldatum;
import de.metas.shipper.gateway.go.schema.SendungsRueckmeldung;
import de.metas.shipper.gateway.go.schema.SendungsRueckmeldung.Sendung.Position;
import de.metas.shipper.gateway.go.schema.Sendungsnummern;
import lombok.Builder;
import lombok.NonNull;

/*
 * #%L
 * de.metas.shipper.go
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

public class GOClient extends WebServiceGatewaySupport implements ShipperGatewayClient
{
	public static final String SHIPPER_GATEWAY_ID = "go";

	private static final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
	private static final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

	private static final Logger logger = LoggerFactory.getLogger(GOClient.class);
	private final ObjectFactory objectFactory = new ObjectFactory();

	private final String requestUsername;
	private final String requestSenderId;

	@Builder
	private GOClient(
			final String url,
			@NonNull final WebServiceMessageSender messageSender,
			@NonNull final Jaxb2Marshaller marshaller,
			final String requestUsername,
			final String requestSenderId)
	{
		Check.assumeNotEmpty(url, "url is not empty");
		Check.assumeNotEmpty(requestUsername, "requestUsername is not empty");
		Check.assumeNotEmpty(requestSenderId, "requestSenderId is not empty");

		setDefaultUri(url);
		setMessageSender(messageSender);
		setMarshaller(marshaller);
		setUnmarshaller(marshaller);

		this.requestUsername = requestUsername;
		this.requestSenderId = requestSenderId;
	}

	@Override
	public String toString()
	{
		return MoreObjects.toStringHelper(this)
				.add("url", getDefaultUri())
				.toString();
	}

	@Override
	public String getShipperGatewayId()
	{
		return SHIPPER_GATEWAY_ID;
	}

	@Override
	public DeliveryOrder createDeliveryOrder(@NonNull final DeliveryOrder draftDeliveryOrder)
	{
		logger.trace("Creating delivery order for {}", draftDeliveryOrder);
		final Sendung goRequest = createGODeliveryOrder(draftDeliveryOrder, GOOrderStatus.NEW);

		final Object goResponseObj = sendAndReceive(objectFactory.createGOWebServiceSendungsErstellung(goRequest));
		final SendungsRueckmeldung goResponse = (SendungsRueckmeldung)goResponseObj;
		final DeliveryOrder deliveryOrderResponse = createDeliveryOrderFromCreateResponse(goResponse, draftDeliveryOrder);
		logger.trace("Delivery order created: {}", deliveryOrderResponse);

		return deliveryOrderResponse;
	}

	@Override
	public DeliveryOrder updateDeliveryOrder(@NonNull final DeliveryOrder deliveryOrder)
	{
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException();
	}

	@Override
	public DeliveryOrder completeDeliveryOrder(@NonNull final DeliveryOrder deliveryOrderRequest)
	{
		logger.trace("Creating delivery order for {}", deliveryOrderRequest);
		final Sendung goRequest = createGODeliveryOrder(deliveryOrderRequest, GOOrderStatus.APPROVED);

		final Object goResponseObj = sendAndReceive(objectFactory.createGOWebServiceSendungsErstellung(goRequest));
		final SendungsRueckmeldung goResponse = (SendungsRueckmeldung)goResponseObj;
		final DeliveryOrder deliveryOrderResponse = createDeliveryOrderFromCreateResponse(goResponse, deliveryOrderRequest);
		logger.trace("Delivery order completed: {}", deliveryOrderResponse);

		return deliveryOrderResponse;
	}

	@Override
	public DeliveryOrder voidDeliveryOrder(@NonNull final DeliveryOrder deliveryOrderRequest)
	{
		logger.trace("Creating delivery order for {}", deliveryOrderRequest);
		final Sendung goRequest = createGODeliveryOrder(deliveryOrderRequest, GOOrderStatus.CANCELLATION);

		final Object goResponseObj = sendAndReceive(objectFactory.createGOWebServiceSendungsErstellung(goRequest));
		final SendungsRueckmeldung goResponse = (SendungsRueckmeldung)goResponseObj;
		final DeliveryOrder deliveryOrderResponse = createDeliveryOrderFromCreateResponse(goResponse, deliveryOrderRequest);
		logger.trace("Delivery order completed: {}", deliveryOrderResponse);

		return deliveryOrderResponse;
	}

	@Override
	public List<PackageLabels> getPackageLabelsList(@NonNull final OrderId orderId) throws ShipperGatewayException
	{
		logger.trace("getPackageLabelsList: orderId={}", orderId);
		final Sendungsnummern goRequest = objectFactory.createSendungsnummern();
		goRequest.getSendungsnummerAX4().add(orderId.getOrderIdAsInt());

		final Object goResponseObj = sendAndReceive(objectFactory.createGOWebServiceSendungsnummern(goRequest));
		if (goResponseObj instanceof Label)
		{
			final Label goLabels = (Label)goResponseObj;
			final List<PackageLabels> packageLabels = createDeliveryPackageLabels(goLabels);
			logger.trace("getPackageLabelsList: got packageLabels={}", packageLabels);
			return packageLabels;
		}
		else if (goResponseObj instanceof Fehlerbehandlung)
		{
			final Fehlerbehandlung errorResponse = (Fehlerbehandlung)goResponseObj;
			throw extractException(errorResponse);
		}
		else
		{
			throw new IllegalArgumentException("Unknown reponse type: " + goResponseObj.getClass());
		}
	}

	private Object sendAndReceive(final JAXBElement<?> goRequestElement)
	{
		if (logger.isTraceEnabled())
		{
			logger.trace("Sending GO Request: {}", toString(goRequestElement));
		}

		final JAXBElement<?> goResponseElement = (JAXBElement<?>)getWebServiceTemplate().marshalSendAndReceive(goRequestElement);
		if (logger.isTraceEnabled())
		{
			logger.trace("Got GO Response: {}", toString(goResponseElement));
		}

		final Object goResponseObj = goResponseElement.getValue();
		return goResponseObj;
	}

	private static final OrderId createOrderId(final String orderIdStr)
	{
		return OrderId.of(SHIPPER_GATEWAY_ID, orderIdStr);
	}

	private ShipperGatewayException extractException(final Fehlerbehandlung errorResponse)
	{
		final Fehlerbehandlung.Fehlermeldungen goErrorMessages = errorResponse.getFehlermeldungen();
		final List<ShipperErrorMessage> shipperErrorMessages = goErrorMessages.getFehler()
				.stream()
				.map(this::createShipperErrorMessage)
				.collect(ImmutableList.toImmutableList());

		return new ShipperGatewayException(shipperErrorMessages);
	}

	private ShipperErrorMessage createShipperErrorMessage(final Fehlerbehandlung.Fehlermeldungen.Fehler goError)
	{
		return ShipperErrorMessage.builder()
				.code(goError.getFehlerNr())
				.message(goError.getBeschreibung())
				.fieldName(goError.getFeld())
				.stackTrace(goError.getStackTrace())
				.build();
	}

	private Sendung createGODeliveryOrder(final DeliveryOrder request, final GOOrderStatus status)
	{
		final OrderId orderId = request.getOrderId();
		final HWBNumber hwbNumber = request.getHwbNumber();

		final Abholadresse pickupAddress = createGOPickupAddress(request.getPickupAddress());
		final Abholdatum pickupDate = createGOPickupDate(request.getPickupDate());

		final Empfaenger deliveryAddress = createGODeliveryAddress(request.getDeliveryAddress(), request.getDeliveryContact());
		final SendungsPosition deliveryPosition = createGODeliveryPosition(request.getDeliveryPosition());

		final Sendung goRequest = newGODeliveryRequest();
		goRequest.setStatus(status.getCode()); // Order status
		goRequest.setSendungsnummerAX4(orderId != null ? orderId.getOrderIdAsString() : null); // AX4 shipment no. (n15, mandatory for update and cancellation)
		goRequest.setFrachtbriefnummer(hwbNumber != null ? hwbNumber.getAsString() : null); // HWB no. (N18, not mandatory). If you provide a HWB no. in this field, AX4 will not generate a new HWB no.

		goRequest.setKundenreferenz(request.getCustomerReference()); // Customer reference no (an40, i guess it's not mandatory)
		goRequest.setAbholadresse(pickupAddress); // Pickup address (mandatory)
		goRequest.setAbholdatum(pickupDate); // Pickup date (mandatory)
		goRequest.setAbholhinweise(request.getPickupNote()); // Pickup note (an128, not mandatory)

		goRequest.setEmpfaenger(deliveryAddress); // Delivery address (mandatory)
		goRequest.setZustelldatum(createGODeliveryDateOrNull(request.getDeliveryDate())); // Delivery date (not mandatory)
		goRequest.setZustellhinweise(request.getDeliveryNote()); // Delivery note (an128, not mandatory)

		goRequest.setService(request.getServiceType().getCode()); // Service type (mandatory)
		goRequest.setUnfrei(request.getPaidMode().getCode()); // Flag unpaid (mandatory)
		goRequest.setSelbstanlieferung(request.getSelfDelivery().getCode()); // Flag self delivery (mandatory)
		goRequest.setSelbstabholung(request.getSelfPickup().getCode()); // Flag self pickup (mandatory)
		goRequest.setWarenwert(null); // Value of goods (not mandatory)
		goRequest.setSonderversicherung(null); // Special insurance (not mandatory)
		goRequest.setNachnahme(null); // Cash on delivery (not mandatory)
		goRequest.setTelefonEmpfangsbestaetigung(request.getReceiptConfirmationPhoneNumber()); // Phone no. for confirmation of receipt (an25, mandatory)

		goRequest.setSendungsPosition(deliveryPosition); // Shipment position (mandatory, max. 1)

		return goRequest;
	}

	private Sendung newGODeliveryRequest()
	{
		final Sendung goRequest = objectFactory.createSendung();
		goRequest.setVersender(requestSenderId);
		goRequest.setBenutzername(requestUsername);
		return goRequest;
	}

	private SendungsPosition createGODeliveryPosition(final DeliveryPosition shipmentPosition)
	{
		final SendungsPosition goShipmentPosition = objectFactory.createSendungSendungsPosition();
		goShipmentPosition.setAnzahlPackstuecke(String.valueOf(shipmentPosition.getNumberOfPackages())); // Number of packages (n9, mandatory)
		goShipmentPosition.setGewicht(String.valueOf(shipmentPosition.getGrossWeightKg())); // Package gross weight (n5, mandatory), in Kg
		goShipmentPosition.setInhalt(shipmentPosition.getContent()); // Content (an40, mandatory)

		final PackageDimensions packageDimensions = shipmentPosition.getPackageDimensions();
		if (packageDimensions != null)
		{
			final Abmessungen goPackageDimensions = objectFactory.createSendungSendungsPositionAbmessungen();
			goPackageDimensions.setLaenge(String.valueOf(packageDimensions.getLengthInCM())); // Length (n6, mandatory), im cm
			goPackageDimensions.setBreite(String.valueOf(packageDimensions.getWidthInCM())); // Width (n6, mandatory), im cm
			goPackageDimensions.setHoehe(String.valueOf(packageDimensions.getHeightInCM())); // Height (n6, mandatory), im cm
			goShipmentPosition.setAbmessungen(goPackageDimensions); // Package dimensions (not mandatory)
		}

		return goShipmentPosition;
	}

	private Abholadresse createGOPickupAddress(final Address pickupAddress)
	{
		final Abholadresse goPickupAddress = objectFactory.createSendungAbholadresse();
		goPickupAddress.setFirmenname1(pickupAddress.getCompanyName1()); // Name 1 (an60, mandatory)
		goPickupAddress.setFirmenname2(pickupAddress.getCompanyName2()); // Name 2 (an60, not mandatory)
		goPickupAddress.setAbteilung(pickupAddress.getCompanyDepartment()); // Department (an40, not mandatory)
		goPickupAddress.setStrasse1(pickupAddress.getStreet1()); // Street 1 (an35, mandatory)
		goPickupAddress.setHausnummer(pickupAddress.getHouseNo()); // House no. (an10, mandatory in DE)
		goPickupAddress.setStrasse2(pickupAddress.getStreet2()); // Street 2 (an25, not mandatory)
		goPickupAddress.setLand(pickupAddress.getCountry().getAlpha2()); // Country (an3, mandatory) // IMPORTANT: it's alpha2
		goPickupAddress.setPostleitzahl(pickupAddress.getZipCode()); // ZIP code (an9, mandatory)
		goPickupAddress.setStadt(pickupAddress.getCity()); // City (an30, mandatory)

		return goPickupAddress;
	}

	private Abholdatum createGOPickupDate(final PickupDate pickupDate)
	{
		final Abholdatum goPickupDate = objectFactory.createSendungAbholdatum();

		final String dateStr = pickupDate.getDate().format(dateFormatter);
		goPickupDate.setDatum(dateStr); // Pickup date (TT.MM.JJJJ, mandatory), shall be >= actual date, < delivery date

		final LocalTime timeFrom = pickupDate.getTimeFrom();
		final String timeFromStr = timeFrom != null ? timeFrom.format(timeFormatter) : null;
		goPickupDate.setUhrzeitVon(timeFromStr); // Pickup time - from (hh:mm, not mandatory)

		final LocalTime timeTo = pickupDate.getTimeFrom();
		final String timeToStr = timeTo != null ? timeTo.format(timeFormatter) : null;
		goPickupDate.setUhrzeitBis(timeToStr); // Pickup time - to (hh:mm, not mandatory)

		return goPickupDate;
	}

	private Empfaenger createGODeliveryAddress(final Address deliveryAddress, final ContactPerson deliveryContact)
	{
		final Empfaenger goDeliveryAddress = objectFactory.createSendungEmpfaenger(); // Consginee address
		goDeliveryAddress.setFirmenname1(deliveryAddress.getCompanyName1()); // Name 1 (an60, mandatory)
		goDeliveryAddress.setFirmenname2(deliveryAddress.getCompanyName2()); // Name 2 (an60, not mandatory)
		goDeliveryAddress.setAbteilung(deliveryAddress.getCompanyDepartment()); // Department (an40, mandatory)
		goDeliveryAddress.setStrasse1(deliveryAddress.getStreet1()); // Street 1 (an35, mandatory)
		goDeliveryAddress.setHausnummer(deliveryAddress.getHouseNo()); // House no. (an10, mandatory in DE)
		goDeliveryAddress.setStrasse2(deliveryAddress.getStreet2()); // Street 2 (an25, not mandatory)
		goDeliveryAddress.setLand(deliveryAddress.getCountry().getAlpha2()); // Country (an3, mandatory) // IMPORTANT: it's alpha2
		goDeliveryAddress.setPostleitzahl(deliveryAddress.getZipCode()); // ZIP code (an9, mandatory)
		goDeliveryAddress.setStadt(deliveryAddress.getCity()); // City (an30, mandatory)

		if (deliveryContact != null)
		{
			final PhoneNumber phone = deliveryContact.getPhone();
			final Telefon goPhone = objectFactory.createSendungEmpfaengerAnsprechpartnerTelefon();
			goPhone.setLaenderPrefix(phone.getCountryCode()); // Country phone area code (n4, mandatory)
			goPhone.setOrtsvorwahl(phone.getAreaCode()); // Area code (n7, mandatory)
			goPhone.setTelefonnummer(phone.getPhoneNumber()); // Phone no. (n10, mandatory)

			final Ansprechpartner goContactPerson = objectFactory.createSendungEmpfaengerAnsprechpartner();
			goContactPerson.setTelefon(goPhone); // Phone (mandatory)
			goDeliveryAddress.setAnsprechpartner(goContactPerson); // Contact person (not mandatory)
		}

		return goDeliveryAddress;
	}

	private Zustelldatum createGODeliveryDateOrNull(final DeliveryDate deliveryDate)
	{
		if (deliveryDate == null)
		{
			return null;
		}

		final Zustelldatum goDeliveryDate = objectFactory.createSendungZustelldatum();

		final String dateStr = deliveryDate.getDate().format(dateFormatter);
		goDeliveryDate.setDatum(dateStr); // Delivery date (TT.MM.JJJJ, mandatory)

		final LocalTime timeFrom = deliveryDate.getTimeFrom();
		final String timeFromStr = timeFrom != null ? timeFrom.format(timeFormatter) : null;
		goDeliveryDate.setUhrzeitVon(timeFromStr); // Delivery time - from (hh:mm, not mandatory)

		final LocalTime timeTo = deliveryDate.getTimeFrom();
		final String timeToStr = timeTo != null ? timeTo.format(timeFormatter) : null;
		goDeliveryDate.setUhrzeitBis(timeToStr); // Delivery time - to (hh:mm, not mandatory)

		return goDeliveryDate;
	}

	private DeliveryOrder createDeliveryOrderFromCreateResponse(final SendungsRueckmeldung goResponse, final DeliveryOrder deliveryOrderRequest)
	{
		final SendungsRueckmeldung.Sendung goResponseContent = goResponse.getSendung();

		// NOTE: based on protocol v1.3 i understand there will be only one position, always
		final List<Position> goDeliveryPositions = goResponseContent.getPosition();
		if (goDeliveryPositions.size() != 1)
		{
			throw new ShipperGatewayException("Only one delivery position was expected but got " + goDeliveryPositions);
		}
		final Position goDeliveryPosition = goDeliveryPositions.get(0);

		return DeliveryOrder.builder()
				.orderId(createOrderId(goResponseContent.getSendungsnummerAX4()))
				.hwbNumber(HWBNumber.of(goResponseContent.getFrachtbriefnummer()))
				//
				.pickupAddress(deliveryOrderRequest.getPickupAddress())
				.pickupDate(PickupDate.builder()
						.date(parseLocalDate(goResponseContent.getAbholdatum()))
						.build())
				.pickupNote(deliveryOrderRequest.getPickupNote())
				//
				.deliveryAddress(deliveryOrderRequest.getDeliveryAddress())
				.deliveryContact(deliveryOrderRequest.getDeliveryContact())
				.deliveryDate(DeliveryDate.builder()
						.date(parseLocalDate(goResponseContent.getZustelldatum()))
						.timeFrom(parseLocalTime(goResponseContent.getZustellUhrzeitVon()))
						.timeTo(parseLocalTime(goResponseContent.getZustellUhrzeitBis()))
						.build())
				.deliveryNote(deliveryOrderRequest.getDeliveryNote())
				.customerReference(deliveryOrderRequest.getCustomerReference())
				//
				.deliveryPosition(deliveryOrderRequest.getDeliveryPosition().toBuilder()
						// .positionNo(goDeliveryPosition.getPositionsNr()) // assume it's always 1
						.numberOfPackages(Integer.parseInt(goDeliveryPosition.getAnzahlPackstuecke()))
						// .barcodes(goDeliveryPosition.getBarcodes().getBarcodeNr())
						.build())
				//
				.serviceType(deliveryOrderRequest.getServiceType())
				.paidMode(deliveryOrderRequest.getPaidMode())
				.selfDelivery(deliveryOrderRequest.getSelfDelivery())
				.selfPickup(deliveryOrderRequest.getSelfPickup())
				.receiptConfirmationPhoneNumber(deliveryOrderRequest.getReceiptConfirmationPhoneNumber())
				.build();
	}

	private static LocalDate parseLocalDate(final String dateStr)
	{
		return !Check.isEmpty(dateStr, true) ? LocalDate.parse(dateStr, dateFormatter) : null;
	}

	private static LocalTime parseLocalTime(final String timeStr)
	{
		return !Check.isEmpty(timeStr, true) ? LocalTime.parse(timeStr, timeFormatter) : null;
	}

	private List<PackageLabels> createDeliveryPackageLabels(final Label goLabels)
	{
		return goLabels.getSendung()
				.stream()
				.map(goPackageLabels -> createPackageLabels(goPackageLabels))
				.collect(ImmutableList.toImmutableList());
	}

	private PackageLabels createPackageLabels(final Label.Sendung goPackageLabels)
	{
		final PDFs pdfs = goPackageLabels.getPDFs();

		return PackageLabels.builder()
				.orderId(createOrderId(goPackageLabels.getSendungsnummerAX4()))
				.hwbNumber(HWBNumber.of(goPackageLabels.getFrachtbriefnummer()))
				.defaultLabelType(GOPackageLabelType.DIN_A6_ROUTER_LABEL)
				.label(PackageLabel.builder()
						.type(GOPackageLabelType.DIN_A4_HWB)
						.contentType(PackageLabel.CONTENTTYPE_PDF)
						.labelData(pdfs.getFrachtbrief())
						.build())
				.label(PackageLabel.builder()
						.type(GOPackageLabelType.DIN_A6_ROUTER_LABEL)
						.contentType(PackageLabel.CONTENTTYPE_PDF)
						.labelData(pdfs.getRouterlabel())
						.build())
				.label(PackageLabel.builder()
						.type(GOPackageLabelType.DIN_A6_ROUTER_LABEL_ZEBRA)
						.contentType(PackageLabel.CONTENTTYPE_PDF)
						.labelData(pdfs.getRouterlabelZebra())
						.build())
				.build();
	}

	private String toString(final JAXBElement<?> jaxbElement)
	{
		final Marshaller marshaller = getMarshaller();
		try
		{
			final StringResult result = new StringResult();
			marshaller.marshal(jaxbElement, result);
			return result.toString();
		}
		catch (final Exception ex)
		{
			throw new RuntimeException("Failed converting " + jaxbElement + " to String", ex);
		}
	}
}
