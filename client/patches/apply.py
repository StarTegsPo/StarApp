print("== WhiteGram: истории и каналы")
patch("TMessagesProj/src/main/java/org/telegram/ui/Stories/DialogStoriesCell.java",
      """    public void openStoryRecorder(long dialogId) {
        if (dialogId == 0) {
            final StoriesController.StoryLimit storyLimit = MessagesController.getInstance(currentAccount).getStoriesController().checkStoryLimit();
            if (storyLimit != null && storyLimit.active(currentAccount)) {
                fragment.showDialog(new LimitReachedBottomSheet(fragment, getContext(), storyLimit.getLimitReachedType(), currentAccount, null));
                return;
            }
        }

        StoryCell cell = null;
        for (int i = 0 ; i < recyclerListView.getChildCount(); i++) {
            StoryCell storyCell = (StoryCell) recyclerListView.getChildAt(i);
            if (dialogId == 0 ? storyCell.isSelf : storyCell.dialogId == dialogId) {
                cell = storyCell;
                break;
            }
        }
        if (cell == null) {
            return;
        }
        final StoryCell finalCell = cell;
        if (dialogId != 0) {
            final Theme.ResourcesProvider resourcesProvider = fragment != null ? fragment.getResourceProvider() : null;
            AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER, resourcesProvider);
            progressDialog.showDelayed(500);
            MessagesController.getInstance(currentAccount).getStoriesController().canSendStoryFor(dialogId, canSend -> {
                progressDialog.dismiss();
                if (canSend) {
                    StoryRecorder.getInstance(fragment.getParentActivity(), currentAccount)
                        .selectedPeerId(dialogId)
                        .canChangePeer(false)
                        .open(StoryRecorder.SourceView.fromStoryCell(finalCell));
                }
            }, true, resourcesProvider);
        } else {
            StoryRecorder.getInstance(fragment.getParentActivity(), currentAccount).open(StoryRecorder.SourceView.fromStoryCell(cell));
        }
    }
""",
      """    public void openStoryRecorder(long dialogId) {
        if (org.startegs.StarAds.blockSwipeStoryRecord() && dialogId == 0) {
            return;
        }
        if (dialogId == 0) {
            final StoriesController.StoryLimit storyLimit = MessagesController.getInstance(currentAccount).getStoriesController().checkStoryLimit();
            if (storyLimit != null && storyLimit.active(currentAccount)) {
                fragment.showDialog(new LimitReachedBottomSheet(fragment, getContext(), storyLimit.getLimitReachedType(), currentAccount, null));
                return;
            }
        }

        StoryCell cell = null;
        for (int i = 0 ; i < recyclerListView.getChildCount(); i++) {
            StoryCell storyCell = (StoryCell) recyclerListView.getChildAt(i);
            if (dialogId == 0 ? storyCell.isSelf : storyCell.dialogId == dialogId) {
                cell = storyCell;
                break;
            }
        }
        if (cell == null) {
            return;
        }
        final StoryCell finalCell = cell;
        if (dialogId != 0) {
            final Theme.ResourcesProvider resourcesProvider = fragment != null ? fragment.getResourceProvider() : null;
            AlertDialog progressDialog = new AlertDialog(getContext(), AlertDialog.ALERT_TYPE_SPINNER, resourcesProvider);
            progressDialog.showDelayed(500);
            MessagesController.getInstance(currentAccount).getStoriesController().canSendStoryFor(dialogId, canSend -> {
                progressDialog.dismiss();
                if (canSend) {
                    StoryRecorder.getInstance(fragment.getParentActivity(), currentAccount)
                        .selectedPeerId(dialogId)
                        .canChangePeer(false)
                        .open(StoryRecorder.SourceView.fromStoryCell(finalCell));
                }
            }, true, resourcesProvider);
        } else {
            org.startegs.StarAds.confirmStoryOpen(fragment, () -> {
                StoryRecorder.getInstance(fragment.getParentActivity(), currentAccount).open(StoryRecorder.SourceView.fromStoryCell(cell));
            });
        }
    }
""")
