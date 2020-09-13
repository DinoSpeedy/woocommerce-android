package com.woocommerce.android.ui.orders.details.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.DiffUtil.Callback
import androidx.recyclerview.widget.RecyclerView
import com.woocommerce.android.R
import com.woocommerce.android.model.OrderShipmentTracking
import com.woocommerce.android.ui.orders.details.adapter.OrderDetailShipmentTrackingListAdapterNew.OrderDetailShipmentTrackingViewHolder
import com.woocommerce.android.util.DateUtils
import kotlinx.android.synthetic.main.order_detail_shipment_tracking_list_item.view.*

class OrderDetailShipmentTrackingListAdapterNew : RecyclerView.Adapter<OrderDetailShipmentTrackingViewHolder>() {
    var shipmentTrackingList: List<OrderShipmentTracking> = ArrayList()
        set(value) {
            val diffResult = DiffUtil.calculateDiff(
                ShipmentTrackingDiffCallback(
                    field,
                    value
                ), true)
            field = value

            diffResult.dispatchUpdatesTo(this)
        }

    override fun onCreateViewHolder(parent: ViewGroup, itemType: Int): OrderDetailShipmentTrackingViewHolder {
        return OrderDetailShipmentTrackingViewHolder(parent)
    }

    override fun onBindViewHolder(holder: OrderDetailShipmentTrackingViewHolder, position: Int) {
        holder.bind(shipmentTrackingList[position])
    }

    override fun getItemCount(): Int = shipmentTrackingList.size

    class OrderDetailShipmentTrackingViewHolder(
        parent: ViewGroup
    ) : RecyclerView.ViewHolder(
        LayoutInflater.from(parent.context).inflate(R.layout.order_detail_shipment_tracking_list_item, parent, false)
    ) {
        fun bind(shipmentTracking: OrderShipmentTracking) {
            with(itemView.tracking_type) { text = shipmentTracking.trackingProvider }
            with(itemView.tracking_number) { text = shipmentTracking.trackingNumber }
            with(itemView.tracking_dateShipped) {
                text = DateUtils.getLocalizedLongDateString(context, shipmentTracking.dateShipped)
            }
        }
    }

    class ShipmentTrackingDiffCallback(
        private val oldList: List<OrderShipmentTracking>,
        private val newList: List<OrderShipmentTracking>
    ) : Callback() {
        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].remoteTrackingId == newList[newItemPosition].remoteTrackingId
        }

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val old = oldList[oldItemPosition]
            val new = newList[newItemPosition]
            return old == new
        }
    }
}
