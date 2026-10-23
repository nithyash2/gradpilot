package com.gradpilot.tracker;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gradpilot.tracker.dto.TrackerDtos.CreateTrackerRequest;
import com.gradpilot.tracker.dto.TrackerDtos.ItemRequest;
import com.gradpilot.tracker.dto.TrackerDtos.ItemResponse;
import com.gradpilot.tracker.dto.TrackerDtos.TrackerDetailResponse;
import com.gradpilot.tracker.dto.TrackerDtos.TrackerResponse;
import com.gradpilot.tracker.dto.TrackerDtos.UpdateTrackerRequest;
import com.gradpilot.web.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class TrackerController {

    private final TrackerService service;
    private final TemplateCatalog templates;

    public TrackerController(TrackerService service, TemplateCatalog templates) {
        this.service = service;
        this.templates = templates;
    }

    @GetMapping("/templates")
    public List<TrackerTemplate> templates() {
        return templates.all();
    }

    @GetMapping("/trackers")
    public List<TrackerResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return service.list(CurrentUser.id(jwt));
    }

    @PostMapping("/trackers")
    @ResponseStatus(HttpStatus.CREATED)
    public TrackerDetailResponse create(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody CreateTrackerRequest request) {
        return service.create(CurrentUser.id(jwt), request);
    }

    @GetMapping("/trackers/{trackerId}")
    public TrackerDetailResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID trackerId) {
        return service.get(CurrentUser.id(jwt), trackerId);
    }

    @PutMapping("/trackers/{trackerId}")
    public TrackerDetailResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID trackerId,
                                        @Valid @RequestBody UpdateTrackerRequest request) {
        return service.update(CurrentUser.id(jwt), trackerId, request);
    }

    @DeleteMapping("/trackers/{trackerId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID trackerId) {
        service.delete(CurrentUser.id(jwt), trackerId);
    }

    @PostMapping("/trackers/{trackerId}/items")
    @ResponseStatus(HttpStatus.CREATED)
    public ItemResponse addItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID trackerId,
                                @Valid @RequestBody ItemRequest request) {
        return service.addItem(CurrentUser.id(jwt), trackerId, request);
    }

    @PutMapping("/trackers/{trackerId}/items/{itemId}")
    public ItemResponse updateItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID trackerId,
                                   @PathVariable UUID itemId, @Valid @RequestBody ItemRequest request) {
        return service.updateItem(CurrentUser.id(jwt), trackerId, itemId, request);
    }

    @DeleteMapping("/trackers/{trackerId}/items/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteItem(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID trackerId,
                           @PathVariable UUID itemId) {
        service.deleteItem(CurrentUser.id(jwt), trackerId, itemId);
    }
}
