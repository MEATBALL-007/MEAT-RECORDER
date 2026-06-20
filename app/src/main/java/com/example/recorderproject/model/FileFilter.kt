package com.example.recorderproject.model

/**
 * Library filter-chip options for the recordings list. Extracted from RecorderViewModel
 * (issue #13 step 8) to live alongside [SortOrder] in the model layer, so the file-library
 * collaborator can own the filtering without a back-dependency on the ViewModel.
 */
enum class FileFilter { ALL, STARRED, LOCKED, NR, EQ }
