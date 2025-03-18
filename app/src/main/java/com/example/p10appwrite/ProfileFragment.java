package com.example.p10appwrite;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.google.android.material.snackbar.Snackbar;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.Query;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.InputFile;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;
import io.appwrite.services.Storage;

public class ProfileFragment extends Fragment {

    ImageView photoImageView;
    TextView displayNameTextView, emailTextView;
    Client client;
    Account account;
    String userId;
    Handler mainHandler;

    private ActivityResultLauncher<String> elegirImagenLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    subirFotoPerfil(uri);
                }
            });

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        photoImageView = view.findViewById(R.id.photoImageView);
        displayNameTextView = view.findViewById(R.id.displayNameTextView);
        emailTextView = view.findViewById(R.id.emailTextView);

        photoImageView.setClickable(true);
        photoImageView.setOnClickListener(v -> seleccionarNuevaFoto());

        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        mainHandler = new Handler(Looper.getMainLooper());

        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                mainHandler.post(() -> {
                    userId = result.getId();
                    displayNameTextView.setText(result.getName().toString());
                    emailTextView.setText(result.getEmail().toString());
                    cargarFotoPerfil(userId);
                });
            }));
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }

    private void seleccionarNuevaFoto() {
        elegirImagenLauncher.launch("image/*");
    }

    private void subirFotoPerfil(Uri uri) {
        Storage storage = new Storage(client);
        File tempFile;
        try {
            tempFile = getFileFromUri(requireContext(), uri);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        storage.createFile(
                getString(R.string.APPWRITE_STORAGE_BUCKET_ID),
                "unique()",
                InputFile.Companion.fromFile(tempFile),
                new ArrayList<>(),
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        System.err.println("Error subiendo la imagen: " + error.getMessage());
                        mainHandler.post(() -> Snackbar.make(requireView(), "Error al subir imagen", Snackbar.LENGTH_SHORT).show());
                        return;
                    }
                    String downloadUrl = "https://cloud.appwrite.io/v1/storage/buckets/" +
                            getString(R.string.APPWRITE_STORAGE_BUCKET_ID) + "/files/" +
                            result.getId() + "/view?project=" + getString(R.string.APPWRITE_PROJECT_ID) +
                            "&mode=admin";
                    try {
                        actualizarFotoPerfil(downloadUrl);
                        System.out.println("Download URL:" + downloadUrl);
                    } catch (AppwriteException e) {
                        throw new RuntimeException(e);
                    }
                })
        );
    }

    private void actualizarFotoPerfil(String downloadUrl) throws AppwriteException {
        Databases databases = new Databases(client);
        ArrayList<String> queries = new ArrayList<>();
        queries.add(Query.Companion.equal("uid", userId));
        databases.listDocuments(
                getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_PROFILE_COLLECTION_ID),
                queries,
                new CoroutineCallback<>((resultPerfil, errorPerfil) -> {
                    if (errorPerfil != null) {
                        errorPerfil.printStackTrace();
                        return;
                    }
                    if (resultPerfil.getDocuments().size() > 0) {
                        String docId = resultPerfil.getDocuments().get(0).getId();
                        Map<String, Object> data = new HashMap<>();
                        data.put("profilePhotoUrl", downloadUrl);
                        try {
                            databases.updateDocument(
                                    getString(R.string.APPWRITE_DATABASE_ID),
                                    getString(R.string.APPWRITE_PROFILE_COLLECTION_ID),
                                    docId,
                                    data,
                                    new ArrayList<>(),
                                    new CoroutineCallback<>((result, error) -> {
                                        if (error != null) {
                                            System.err.println("Error al actualizar el perfil: " + error.toString());
                                            return;
                                        }
                                        mainHandler.post(() -> {
                                            Glide.with(requireContext()).load(downloadUrl).into(photoImageView);
                                            Snackbar.make(requireView(), "Foto de perfil actualizada", Snackbar.LENGTH_SHORT).show();
                                            AppViewModel appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
                                            appViewModel.profilePhotoUrl.setValue(downloadUrl);
                                        });
                                    })
                            );
                        } catch (AppwriteException e) {
                            e.printStackTrace();
                        }
                    }
                })
        );
    }

    private void cargarFotoPerfil(String uid) {
        Databases databases = new Databases(client);
        ArrayList<String> queries = new ArrayList<>();
        queries.add(Query.Companion.equal("uid", uid));
        try {
            databases.listDocuments(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_PROFILE_COLLECTION_ID),
                    queries,
                    new CoroutineCallback<>((resultPerfil, errorPerfil) -> {
                        if (errorPerfil != null) {
                            errorPerfil.printStackTrace();
                            return;
                        }
                        if (resultPerfil.getDocuments().size() > 0) {
                            Map<String, Object> perfil = resultPerfil.getDocuments().get(0).getData();
                            String urlFoto = (String) perfil.get("profilePhotoUrl");
                            mainHandler.post(() -> {
                                if (urlFoto != null && !urlFoto.isEmpty()) {
                                    Glide.with(requireContext()).load(urlFoto).into(photoImageView);
                                    AppViewModel appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
                                    appViewModel.profilePhotoUrl.setValue(urlFoto);
                                } else {
                                    Glide.with(requireContext()).load(R.drawable.user).into(photoImageView);
                                }
                            });
                        } else {
                            mainHandler.post(() -> Glide.with(requireContext()).load(R.drawable.user).into(photoImageView));
                        }
                    })
            );
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }

    public File getFileFromUri(Context context, Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new FileNotFoundException("No se pudo abrir el URI: " + uri);
        }
        String fileName = getFileName(context, uri);
        File tempFile = new File(context.getCacheDir(), fileName);
        FileOutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.close();
        inputStream.close();
        return tempFile;
    }

    private String getFileName(Context context, Uri uri) {
        String fileName = "temp_file";
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
            }
        }
        return fileName;
    }
}
