package com.google.mediapipe.apps.wearableai.ui;

// some code taken from https://github.com/stairs1/memory-expansion-tools/blob/master/AndroidMXT/app/src/main/java/com/memoryexpansiontools/mxt/StreamFragment.java

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import java.util.ArrayList;
import android.widget.EditText;

import java.util.Arrays;
import java.util.List;
import java.lang.Long;

import com.google.mediapipe.apps.wearableai.database.phrase.Phrase;
import com.google.mediapipe.apps.wearableai.database.voicecommand.VoiceCommandEntity;
import com.google.mediapipe.apps.wearableai.database.voicecommand.VoiceCommandViewModel;
import com.google.mediapipe.apps.wearableai.database.phrase.PhraseViewModel;

import com.google.mediapipe.apps.wearableai.ui.ItemClickListener;

import com.google.mediapipe.apps.wearableai.R;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link StreamFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MxtCacheUi extends Fragment implements ItemClickListener {
    public String TAG = "WearableAi_MxtCacheUi";

    private VoiceCommandViewModel mVoiceCommandViewModel;
    private PhraseViewModel mPhraseViewModel;

    @Override
    public void onClick(View view, Phrase phrase){
//        Intent intent = new Intent(MainActivity.this, ViewVoiceCommandActivity.class);
//        intent.putExtra("voiceCommand", voiceCommand.getId());
//        startActivityForResult(intent, VIEW_PHRASE_ACTIVITY_REQUEST_CODE);
        //pass on this for now, need to make the voiceCommand view thing a fragment as well
        Log.d("cayden", "click on stream");
    }

    public MxtCacheUi() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.mxt_cache_fragment, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState){
        RecyclerView recyclerView = view.findViewById(R.id.phrase_wall);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        linearLayoutManager.setReverseLayout(true);
        final PhraseListAdapter adapter = new PhraseListAdapter(getContext());
        adapter.setClickListener(this);
        recyclerView.setAdapter(adapter);
        recyclerView.setLayoutManager(linearLayoutManager);
        recyclerView.scrollToPosition(recyclerView.getAdapter().getItemCount() -1);


        // Get a new or existing ViewModel from the ViewModelProvider.
        mVoiceCommandViewModel = new ViewModelProvider(this).get(VoiceCommandViewModel.class);
        mPhraseViewModel = new ViewModelProvider(this).get(PhraseViewModel.class);

        //here we need to get all of the MXT commands, and then pull in each phrase associated with each command
        mVoiceCommandViewModel.getMxtCache().observe(getActivity(), new Observer<List<Phrase>>() {
            @Override
            public void onChanged(@Nullable final List<Phrase> phrases) {
                //get all phrases associated with latest mxt commands, then
                // Update the cached copy of the words in the adapter.
//                Log.d(TAG, "mxt cache");
//                Log.d(TAG, Long.toString(voiceCommands.get(0).getTranscriptId()));
//                List<Long> phraseIds = new ArrayList<Long>();
//                voiceCommands.forEach( (voiceCommand) -> phraseIds.add(voiceCommand.getTranscriptId()) );
//                Log.d(TAG, Arrays.toString(phraseIds.toArray()));
//                List<Phrase> phrases = mPhraseViewModel.getPhrases(phraseIds);
                adapter.setPhrases(phrases);
            }
        });

//        EditText editText = view.findViewById(R.id.add_voiceCommand_text);
//        Button submitButton = view.findViewById(R.id.submit_button);
//        submitButton.setOnClickListener(new View.OnClickListener() {
//            @Override
//            public void onClick(View v) {
//                String words = editText.getText().toString();
//                if(!words.isEmpty()) {
//                    editText.setText("");
//                    mVoiceCommandViewModel.addVoiceCommand(words, getString(R.string.medium_text));
//                }
//            }
//        });

    }
}

