package com.woocommerce.android.ui.orders

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.clearInvocations
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import com.woocommerce.android.AppPrefsWrapper
import com.woocommerce.android.R.string
import com.woocommerce.android.extensions.takeIfNotEqualTo
import com.woocommerce.android.model.Order
import com.woocommerce.android.model.Order.OrderStatus
import com.woocommerce.android.model.OrderNote
import com.woocommerce.android.model.OrderShipmentTracking
import com.woocommerce.android.model.Refund
import com.woocommerce.android.model.RequestResult
import com.woocommerce.android.model.ShippingLabel
import com.woocommerce.android.tools.NetworkStatus
import com.woocommerce.android.tools.SelectedSite
import com.woocommerce.android.ui.orders.details.OrderDetailFragmentNewArgs
import com.woocommerce.android.ui.orders.details.OrderDetailRepositoryNew
import com.woocommerce.android.ui.orders.details.OrderDetailViewModelNew
import com.woocommerce.android.ui.orders.details.OrderDetailViewModelNew.OrderDetailViewState
import com.woocommerce.android.util.CoroutineTestRule
import com.woocommerce.android.viewmodel.BaseUnitTest
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowSnackbar
import com.woocommerce.android.viewmodel.MultiLiveEvent.Event.ShowUndoSnackbar
import com.woocommerce.android.viewmodel.ResourceProvider
import com.woocommerce.android.viewmodel.SavedStateWithArgs
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runBlockingTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.wordpress.android.fluxc.network.rest.wpcom.wc.order.CoreOrderStatus

@ExperimentalCoroutinesApi
class OrderDetailViewModelTest : BaseUnitTest() {
    companion object {
        private const val ORDER_IDENTIFIER = "1-1-1"
    }

    private val networkStatus: NetworkStatus = mock()
    private val appPrefsWrapper: AppPrefsWrapper = mock()
    private val selectedSite: SelectedSite = mock()
    private val repository: OrderDetailRepositoryNew = mock()
    private val resources: ResourceProvider = mock {
        on(it.getString(any(), any())).thenAnswer { i -> i.arguments[0].toString() }
    }

    private val savedState: SavedStateWithArgs = spy(
        SavedStateWithArgs(
            SavedStateHandle(),
            null,
            OrderDetailFragmentNewArgs(orderId = ORDER_IDENTIFIER)
        )
    )

    @get:Rule
    var coroutinesTestRule = CoroutineTestRule()
    private val order = OrderTestUtils.generateTestOrder(ORDER_IDENTIFIER)
    private val orderStatus = OrderStatus(order.status.value, order.status.value)
    private val testOrderNotes = OrderTestUtils.generateTestOrderNotes(5, ORDER_IDENTIFIER)
    private val testOrderShipmentTrackings = OrderTestUtils.generateTestOrderShipmentTrackings(5, ORDER_IDENTIFIER)
    private val orderShippingLabels = OrderTestUtils.generateShippingLabels(5, ORDER_IDENTIFIER)
    private val testOrderRefunds = OrderTestUtils.generateRefunds(1)
    private lateinit var viewModel: OrderDetailViewModelNew

    private val orderWithParameters = OrderDetailViewState(
        order = order,
        isRefreshing = false,
        isOrderNotesSkeletonShown = false,
        isOrderDetailSkeletonShown = false,
        toolbarTitle = resources.getString(string.orderdetail_orderstatus_ordernum, order.number),
        isShipmentTrackingAvailable = true
    )

    @Before
    fun setup() {
        doReturn(MutableLiveData(OrderDetailViewState()))
            .whenever(savedState).getLiveData<OrderDetailViewState>(any(), any())
        doReturn(true).whenever(networkStatus).isConnected()

        viewModel = spy(OrderDetailViewModelNew(
            savedState,
            coroutinesTestRule.testDispatchers,
            networkStatus,
            appPrefsWrapper,
            resources,
            repository
        ))

        clearInvocations(
            viewModel,
            savedState,
            selectedSite,
            repository,
            networkStatus,
            resources
        )
    }

    @Test
    fun `Displays the order detail view correctly`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(order).whenever(repository).getOrder(any())

        doReturn(true).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())

        doReturn(emptyList<Refund>()).whenever(repository).getOrderRefunds(any())
        doReturn(emptyList<Refund>()).whenever(repository).fetchOrderRefunds(any())

        doReturn(emptyList<ShippingLabel>()).whenever(repository).getOrderShippingLabels(any())
        doReturn(emptyList<ShippingLabel>()).whenever(repository).fetchOrderShippingLabels(any())

        var orderData: OrderDetailViewState? = null
        viewModel.orderDetailViewStateData.observeForever { _, new -> orderData = new }

        // order notes
        val orderNotes = ArrayList<OrderNote>()
        viewModel.orderNotes.observeForever {
            it?.let { orderNotes.addAll(it) }
        }

        // order shipment Trackings
        val shipmentTrackings = ArrayList<OrderShipmentTracking>()
        viewModel.shipmentTrackings.observeForever {
            it?.let { shipmentTrackings.addAll(it) }
        }

        // product list should not be empty when shipping labels are not available and products are not refunded
        val products = ArrayList<Order.Item>()
        viewModel.productList.observeForever {
            it?.let { products.addAll(it) }
        }

        // refunds
        val refunds = ArrayList<Refund>()
        viewModel.orderRefunds.observeForever {
            it?.let { refunds.addAll(it) }
        }

        // shipping Labels
        val shippingLabels = ArrayList<ShippingLabel>()
        viewModel.shippingLabels.observeForever {
            it?.let { shippingLabels.addAll(it) }
        }

        viewModel.start()

        assertThat(orderData).isEqualTo(orderWithParameters)

        assertThat(orderNotes).isNotEmpty
        assertThat(orderNotes).isEqualTo(testOrderNotes)

        assertThat(shipmentTrackings).isNotEmpty
        assertThat(shipmentTrackings).isEqualTo(testOrderShipmentTrackings)

        assertThat(products).isNotEmpty
        assertThat(refunds).isEmpty()
        assertThat(shippingLabels).isEmpty()
    }

    @Test
    fun `Do not display product list when all products are refunded`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(order).whenever(repository).getOrder(any())

        doReturn(true).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())

        doReturn(testOrderRefunds).whenever(repository).fetchOrderRefunds(any())
        doReturn(testOrderRefunds).whenever(repository).getOrderRefunds(any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())

        doReturn(emptyList<ShippingLabel>()).whenever(repository).getOrderShippingLabels(any())
        doReturn(emptyList<ShippingLabel>()).whenever(repository).fetchOrderShippingLabels(any())

        // refunds
        val refunds = ArrayList<Refund>()
        viewModel.orderRefunds.observeForever {
            it?.let { refunds.addAll(it) }
        }

        var products = emptyList<Order.Item>()
        viewModel.productList.observeForever {
            it?.let { products = it }
        }

        viewModel.start()

        assertThat(refunds).isNotEmpty
        assertThat(products).isEmpty()
    }

    @Test
    fun `Do not display product list when shipping labels are available`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(order).whenever(repository).getOrder(any())

        doReturn(true).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())

        doReturn(emptyList<Refund>()).whenever(repository).fetchOrderRefunds(any())
        doReturn(emptyList<Refund>()).whenever(repository).getOrderRefunds(any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())

        doReturn(orderShippingLabels).whenever(repository).getOrderShippingLabels(any())
        doReturn(orderShippingLabels).whenever(repository).fetchOrderShippingLabels(any())

        val shippingLabels = ArrayList<ShippingLabel>()
        viewModel.shippingLabels.observeForever {
            it?.let { shippingLabels.addAll(it) }
        }

        var products = emptyList<Order.Item>()
        viewModel.productList.observeForever {
            it?.let { products = it }
        }

        viewModel.start()

        assertThat(shippingLabels).isNotEmpty
        assertThat(products).isEmpty()
    }

    @Test
    fun `Do not display shipment tracking when shipping labels are available`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(order).whenever(repository).getOrder(any())

        doReturn(true).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())

        doReturn(emptyList<Refund>()).whenever(repository).fetchOrderRefunds(any())
        doReturn(emptyList<Refund>()).whenever(repository).getOrderRefunds(any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())

        doReturn(orderShippingLabels).whenever(repository).getOrderShippingLabels(any())
        doReturn(orderShippingLabels).whenever(repository).fetchOrderShippingLabels(any())

        var orderData: OrderDetailViewState? = null
        viewModel.orderDetailViewStateData.observeForever { _, new -> orderData = new }

        val shippingLabels = ArrayList<ShippingLabel>()
        viewModel.shippingLabels.observeForever {
            it?.let { shippingLabels.addAll(it) }
        }

        // order shipment Trackings
        var shipmentTrackings = emptyList<OrderShipmentTracking>()
        viewModel.shipmentTrackings.observeForever {
            it?.let { shipmentTrackings = it }
        }

        viewModel.start()

        assertThat(shippingLabels).isNotEmpty
        assertThat(shipmentTrackings).isEmpty()
        assertThat(orderData?.isShipmentTrackingAvailable).isFalse()
    }

    @Test
    fun `Display error message on fetch order error`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        whenever(repository.fetchOrder(ORDER_IDENTIFIER)).thenReturn(null)
        whenever(repository.getOrder(ORDER_IDENTIFIER)).thenReturn(null)

        var snackbar: ShowSnackbar? = null
        viewModel.event.observeForever {
            if (it is ShowSnackbar) snackbar = it
        }

        viewModel.start()

        verify(repository, times(1)).fetchOrder(ORDER_IDENTIFIER)

        assertThat(snackbar).isEqualTo(ShowSnackbar(string.order_error_fetch_generic))
    }

    @Test
    fun `Shows and hides order detail skeleton correctly`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(null).whenever(repository).getOrder(any())

        val isSkeletonShown = ArrayList<Boolean>()
        viewModel.orderDetailViewStateData.observeForever { old, new ->
            new.isOrderDetailSkeletonShown?.takeIfNotEqualTo(old?.isOrderDetailSkeletonShown) {
                isSkeletonShown.add(it)
            } }

        viewModel.start()
        assertThat(isSkeletonShown).containsExactly(false, true, false)
    }

    @Test
    fun `Do not fetch order from api when not connected`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(null).whenever(repository).getOrder(any())
        doReturn(false).whenever(networkStatus).isConnected()

        var snackbar: ShowSnackbar? = null
        viewModel.event.observeForever {
            if (it is ShowSnackbar) snackbar = it
        }

        viewModel.start()

        verify(repository, times(1)).getOrder(ORDER_IDENTIFIER)
        verify(repository, times(0)).fetchOrder(any())

        assertThat(snackbar).isEqualTo(ShowSnackbar(string.offline_error))
    }

    @Test
    fun `Update order status and handle undo action`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        val newOrderStatus = OrderStatus(CoreOrderStatus.PROCESSING.value, CoreOrderStatus.PROCESSING.value)

        doReturn(order).whenever(repository).getOrder(any())
        doReturn(orderStatus).doReturn(newOrderStatus).doReturn(orderStatus).whenever(repository).getOrderStatus(any())

        doReturn(true).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())

        doReturn(emptyList<Refund>()).whenever(repository).fetchOrderRefunds(any())
        doReturn(emptyList<Refund>()).whenever(repository).getOrderRefunds(any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())

        doReturn(emptyList<ShippingLabel>()).whenever(repository).getOrderShippingLabels(any())
        doReturn(emptyList<ShippingLabel>()).whenever(repository).fetchOrderShippingLabels(any())

        var snackbar: ShowUndoSnackbar? = null
        viewModel.event.observeForever {
            if (it is ShowUndoSnackbar) snackbar = it
        }

        val orderStatusList = ArrayList<OrderStatus>()
        viewModel.orderDetailViewStateData.observeForever { old, new ->
            new.orderStatus?.takeIfNotEqualTo(old?.orderStatus) { orderStatusList.add(it) }
        }

        viewModel.start()

        val oldStatus = order.status
        val newStatus = CoreOrderStatus.PROCESSING.value
        viewModel.onOrderStatusChanged(newStatus)

        assertThat(snackbar?.message).isEqualTo(resources.getString(string.order_status_changed_to, newStatus))

        // simulate undo click event
        viewModel.onOrderStatusChangeReverted()
        assertThat(orderStatusList).containsExactly(
            OrderStatus(statusKey = oldStatus.value, label = oldStatus.value),
            OrderStatus(statusKey = newStatus, label = newStatus),
            OrderStatus(statusKey = oldStatus.value, label = oldStatus.value)
        )
    }

    @Test
    fun `Update order status when network connected`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        val newOrderStatus = OrderStatus(CoreOrderStatus.PROCESSING.value, CoreOrderStatus.PROCESSING.value)

        doReturn(order).whenever(repository).getOrder(any())
        doReturn(orderStatus).doReturn(newOrderStatus).whenever(repository).getOrderStatus(any())

        doReturn(true).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())

        doReturn(emptyList<Refund>()).whenever(repository).fetchOrderRefunds(any())
        doReturn(emptyList<Refund>()).whenever(repository).getOrderRefunds(any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())

        doReturn(emptyList<ShippingLabel>()).whenever(repository).getOrderShippingLabels(any())
        doReturn(emptyList<ShippingLabel>()).whenever(repository).fetchOrderShippingLabels(any())

        var newOrder: Order? = null
        viewModel.orderDetailViewStateData.observeForever { old, new ->
            new.order?.takeIfNotEqualTo(old?.order) { newOrder = it }
        }

        viewModel.start()
        viewModel.onOrderStatusChanged(CoreOrderStatus.PROCESSING.value)

        assertThat(newOrder?.status).isEqualTo(order.status)
    }

    @Test
    fun `Do not update order status when not connected`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(order).whenever(repository).getOrder(any())
        doReturn(false).whenever(networkStatus).isConnected()

        doReturn(false).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())

        doReturn(emptyList<ShippingLabel>()).whenever(repository).getOrderShippingLabels(any())
        doReturn(emptyList<ShippingLabel>()).whenever(repository).fetchOrderShippingLabels(any())

        var snackbar: ShowSnackbar? = null
        viewModel.event.observeForever {
            if (it is ShowSnackbar) snackbar = it
        }

        viewModel.start()
        viewModel.onOrderStatusChanged(CoreOrderStatus.PROCESSING.value)
        viewModel.updateOrderStatus(CoreOrderStatus.PROCESSING.value)

        verify(repository, times(0)).updateOrderStatus(any(), any(), any())

        assertThat(snackbar).isEqualTo(ShowSnackbar(string.offline_error))
    }

    @Test
    fun `Do not add order note when not connected`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(order).whenever(repository).getOrder(any())
        doReturn(false).whenever(networkStatus).isConnected()

        doReturn(false).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())

        doReturn(emptyList<ShippingLabel>()).whenever(repository).getOrderShippingLabels(any())
        doReturn(emptyList<ShippingLabel>()).whenever(repository).fetchOrderShippingLabels(any())

        var snackbar: ShowSnackbar? = null
        viewModel.event.observeForever {
            if (it is ShowSnackbar) snackbar = it
        }

        viewModel.start()
        viewModel.onNewOrderNoteAdded(testOrderNotes[0])

        verify(repository, times(0)).addOrderNote(any(), any(), any())
        assertThat(snackbar).isEqualTo(ShowSnackbar(string.offline_error))
    }

    @Test
    fun `Add order note - Displays add note snackbar correctly`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(order).whenever(repository).getOrder(any())

        doReturn(false).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())
        doReturn(true).whenever(repository).addOrderNote(any(), any(), any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())

        doReturn(emptyList<ShippingLabel>()).whenever(repository).getOrderShippingLabels(any())
        doReturn(emptyList<ShippingLabel>()).whenever(repository).fetchOrderShippingLabels(any())

        var snackbar: ShowSnackbar? = null
        viewModel.event.observeForever {
            if (it is ShowSnackbar) snackbar = it
        }

        var orderNotes = emptyList<OrderNote>()
        viewModel.orderNotes.observeForever {
            it?.let { orderNotes = it }
        }

        val orderNote = OrderNote(note = "Testing new order note", isCustomerNote = true)
        viewModel.start()
        viewModel.onNewOrderNoteAdded(orderNote)

        verify(repository, times(1)).addOrderNote(any(), any(), any())
        assertThat(snackbar).isEqualTo(ShowSnackbar(string.add_order_note_added))
        assertThat(orderNotes.size).isEqualTo(testOrderNotes.size + 1)
    }

    @Test
    fun `Do not add shipment tracking when not connected`() = coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(order).whenever(repository).getOrder(any())
        doReturn(false).whenever(networkStatus).isConnected()

        doReturn(false).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())

        doReturn(emptyList<ShippingLabel>()).whenever(repository).getOrderShippingLabels(any())
        doReturn(emptyList<ShippingLabel>()).whenever(repository).fetchOrderShippingLabels(any())

        var snackbar: ShowSnackbar? = null
        viewModel.event.observeForever {
            if (it is ShowSnackbar) snackbar = it
        }

        viewModel.start()

        val shipmentTracking = testOrderShipmentTrackings[0].copy(isCustomProvider = false)
        viewModel.onNewShipmentTrackingAdded(shipmentTracking)

        verify(repository, times(0)).addOrderNote(any(), any(), any())
        assertThat(snackbar).isEqualTo(ShowSnackbar(string.offline_error))
    }

    @Test
    fun `Add shipment tracking - Displays add shipment tracking snackbar correctly`() =
        coroutinesTestRule.testDispatcher.runBlockingTest {
        doReturn(order).whenever(repository).getOrder(any())

        doReturn(false).whenever(repository).fetchOrderNotes(any(), any())
        doReturn(testOrderNotes).whenever(repository).getOrderNotes(any())

        doReturn(RequestResult.SUCCESS).whenever(repository).fetchOrderShipmentTrackingList(any(), any())
        doReturn(testOrderShipmentTrackings).whenever(repository).getOrderShipmentTrackings(any())
        doReturn(true).whenever(repository).addOrderShipmentTracking(any(), any(), any(), any())

        doReturn(emptyList<ShippingLabel>()).whenever(repository).getOrderShippingLabels(any())
        doReturn(emptyList<ShippingLabel>()).whenever(repository).fetchOrderShippingLabels(any())

        var snackbar: ShowSnackbar? = null
        viewModel.event.observeForever {
            if (it is ShowSnackbar) snackbar = it
        }

        var orderShipmentTrackings = emptyList<OrderShipmentTracking>()
        viewModel.shipmentTrackings.observeForever {
            it?.let { orderShipmentTrackings = it }
        }

        val shipmentTracking = OrderShipmentTracking(
            trackingNumber = "12345",
            trackingProvider = "test",
            dateShipped = "132434323",
            isCustomProvider = true
        )
        viewModel.start()
        viewModel.onNewShipmentTrackingAdded(shipmentTracking)

        verify(repository, times(1)).addOrderShipmentTracking(any(), any(), any(), any())
        assertThat(snackbar).isEqualTo(ShowSnackbar(string.order_shipment_tracking_added))
        assertThat(orderShipmentTrackings.size).isEqualTo(testOrderShipmentTrackings.size + 1)
    }
}
