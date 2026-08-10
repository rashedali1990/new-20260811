package com.example.m3uplayer

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.m3uplayer.databinding.ActivitySeriesDetailBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SeriesDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SERVER     = "extra_server"
        const val EXTRA_USERNAME   = "extra_username"
        const val EXTRA_PASSWORD   = "extra_password"
        const val EXTRA_SERIES_ID  = "extra_series_id"
        const val EXTRA_SERIES_NAME = "extra_series_name"
    }

    private lateinit var binding: ActivitySeriesDetailBinding
    private lateinit var favoritesManager: FavoritesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySeriesDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        favoritesManager = FavoritesManager(this)

        val server     = intent.getStringExtra(EXTRA_SERVER)!!
        val username   = intent.getStringExtra(EXTRA_USERNAME)!!
        val password   = intent.getStringExtra(EXTRA_PASSWORD)!!
        val seriesId   = intent.getStringExtra(EXTRA_SERIES_ID)!!
        val seriesName = intent.getStringExtra(EXTRA_SERIES_NAME) ?: ""

        title = seriesName
        binding.recyclerEpisodes.layoutManager = LinearLayoutManager(this)

        binding.progressBar.visibility = android.view.View.VISIBLE
        lifecycleScope.launch {
            try {
                val episodes = withContext(Dispatchers.IO) {
                    XtreamClient.fetchSeriesEpisodes(server, username, password, seriesId)
                }
                if (episodes.isEmpty()) {
                    Toast.makeText(
                        this@SeriesDetailActivity,
                        R.string.no_channels_found,
                        Toast.LENGTH_SHORT
                    ).show()
                }
                // تمرير المعاملات الأربعة المطلوبة لـ MediaAdapter
                binding.recyclerEpisodes.adapter = MediaAdapter(
                    items          = episodes,
                    onClick        = { episode ->
                        if (episode.playUrl != null) {
                            val intent = Intent(this@SeriesDetailActivity, PlayerActivity::class.java).apply {
                                putExtra(PlayerActivity.EXTRA_STREAM_URL,  episode.playUrl)
                                putExtra(PlayerActivity.EXTRA_STREAM_NAME, "$seriesName - ${episode.title}")
                            }
                            startActivity(intent)
                        }
                    },
                    onFavoriteClick = { episode ->
                        favoritesManager.toggleFavorite(episode.id)
                    },
                    isFavorite      = { episode -> favoritesManager.isFavorite(episode.id) }
                )
            } catch (e: Exception) {
                Toast.makeText(
                    this@SeriesDetailActivity,
                    getString(R.string.load_error, e.message),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                binding.progressBar.visibility = android.view.View.GONE
            }
        }
    }
}
