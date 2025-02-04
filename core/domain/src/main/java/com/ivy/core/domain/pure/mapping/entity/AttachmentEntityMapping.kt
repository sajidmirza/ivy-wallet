package com.ivy.core.domain.pure.mapping.entity

import com.ivy.core.persistence.entity.attachment.AttachmentEntity
import com.ivy.data.attachment.Attachment

fun mapToEntity(attachment: Attachment): AttachmentEntity = with(attachment) {
    AttachmentEntity(
        id = id,
        associatedId = associatedId,
        uri = uri,
        source = source,
        filename = filename,
        type = type,
        sync = sync
    )
}