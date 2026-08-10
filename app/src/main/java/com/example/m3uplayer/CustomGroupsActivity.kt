package com.example.m3uplayer

import android.os.Bundle
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.m3uplayer.databinding.ActivityCustomGroupsBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CustomGroupsActivity : AppCompatActivity() {
    private lateinit var binding: ActivityCustomGroupsBinding
    private lateinit var groupManager: CustomGroupManager
    private lateinit var adapter: CustomGroupAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCustomGroupsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        groupManager = CustomGroupManager(this)
        setupRecyclerView()

        binding.fabAddGroup.setOnClickListener {
            showCreateGroupDialog()
        }
    }

    private fun setupRecyclerView() {
        adapter = CustomGroupAdapter(
            groupManager.getGroups(),
            onDeleteClick = { group ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("حذف المجموعة")
                    .setMessage("هل أنت متأكد من حذف ${group.name}؟")
                    .setPositiveButton("حذف") { _, _ ->
                        groupManager.deleteGroup(group.id)
                        refreshGroups()
                    }
                    .setNegativeButton("إلغاء", null)
                    .show()
            },
            onGroupClick = { group ->
                Toast.makeText(this, "تم اختيار ${group.name}", Toast.LENGTH_SHORT).show()
                // Later: navigate to group content view
            }
        )
        binding.recyclerGroups.layoutManager = LinearLayoutManager(this)
        binding.recyclerGroups.adapter = adapter
    }

    private fun showCreateGroupDialog() {
        val input = EditText(this).apply {
            hint = "أدخل اسم المجموعة"
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("إنشاء مجموعة جديدة")
            .setView(input)
            .setPositiveButton("حفظ") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    groupManager.createGroup(name)
                    refreshGroups()
                } else {
                    Toast.makeText(this, "يرجى إدخال اسم المجموعة", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    private fun refreshGroups() {
        adapter.updateGroups(groupManager.getGroups())
    }
}
