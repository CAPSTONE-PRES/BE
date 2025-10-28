package com.pres.pres_server.dto.file;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor
@AllArgsConstructor
public class QrInfoDto {
    public String qrSlug;
    public String qrUrl;
}
