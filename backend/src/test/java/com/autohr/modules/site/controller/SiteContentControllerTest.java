package com.autohr.modules.site.controller;

import com.autohr.common.exception.BusinessException;
import com.autohr.modules.site.dto.SiteContentSaveRequest;
import com.autohr.modules.site.service.SiteContentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class SiteContentControllerTest {

    @Mock
    SiteContentService siteContentService;

    @InjectMocks
    SiteContentController controller;

    @Test
    void rejectsUnknownFieldsBeforeTheyReachTheFileStore() {
        SiteContentSaveRequest request = new SiteContentSaveRequest();
        request.captureUnknownField("debug", true);

        assertThrows(BusinessException.class, () -> controller.save(request));
        verifyNoInteractions(siteContentService);
    }
}
