package com.example.iqmemo.ui.notebook

import android.content.Context
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import com.example.iqmemo.R
import com.example.iqmemo.databinding.DialogNotebookDeleteActionsBinding

internal object NotebookDeleteDialog {
    fun show(context: Context, message: String, confirm: () -> Unit): AlertDialog {
        val actions=DialogNotebookDeleteActionsBinding.inflate(LayoutInflater.from(context))
        val dialog=AlertDialog.Builder(context).setTitle(R.string.notebook_delete_title)
            .setMessage(message).setView(actions.root).create()
        actions.btnCancelDelete.setOnClickListener {dialog.dismiss()}
        actions.btnConfirmDelete.setOnClickListener {dialog.dismiss();confirm()}
        dialog.show()
        return dialog
    }
}
