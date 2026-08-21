package com.custom.wallpaper

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.Executors

class HistoryAdapter(
    private val historyList: MutableList<HistoryItem>,
    private val onRestore: (HistoryItem) -> Unit,
    private val onDelete: (HistoryItem) -> Unit
) : RecyclerView.Adapter<HistoryAdapter.ViewHolder>() {

    private val executor = Executors.newFixedThreadPool(4)
    private val handler = Handler(Looper.getMainLooper())

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.historyThumbnail)
        val typeTag: TextView = view.findViewById(R.id.historyTypeTag)
        val deleteBtn: ImageView = view.findViewById(R.id.historyDeleteBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_history, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = historyList[position]
        holder.typeTag.text = item.type.uppercase()

        // Clear previous image to prevent recycling artifact
        holder.thumbnail.setImageBitmap(null)
        holder.thumbnail.setBackgroundColor(android.graphics.Color.parseColor("#e4e4e7")) // field color

        if (item.type == "video") {
            executor.execute {
                var bitmap: Bitmap? = null
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(holder.itemView.context, Uri.parse(item.uri))
                    bitmap = retriever.getFrameAtTime(0)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try { retriever.release() } catch (e: Exception) {}
                }
                
                handler.post {
                    if (bitmap != null) {
                        holder.thumbnail.setImageBitmap(bitmap)
                    }
                }
            }
        } else {
            // Web/HTML generic placeholder (since WebView thumbnails require active rendering)
            holder.thumbnail.setImageResource(R.drawable.ic_settings)
            holder.thumbnail.scaleType = ImageView.ScaleType.CENTER
        }

        holder.itemView.setOnClickListener { onRestore(item) }
        holder.deleteBtn.setOnClickListener {
            val currentPos = holder.adapterPosition
            if (currentPos != RecyclerView.NO_POSITION) {
                onDelete(item)
                historyList.removeAt(currentPos)
                notifyItemRemoved(currentPos)
            }
        }
    }

    override fun getItemCount() = historyList.size
    
    fun updateData(newList: List<HistoryItem>) {
        historyList.clear()
        historyList.addAll(newList)
        notifyDataSetChanged()
    }
}
