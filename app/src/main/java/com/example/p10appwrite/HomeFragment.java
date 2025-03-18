package com.example.p10appwrite;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.Query;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;

public class HomeFragment extends Fragment {
    private NavController navController;
    PostsAdapter adapter;
    AppViewModel appViewModel;

    ImageView photoImageView;
    TextView displayNameTextView, emailTextView;
    Client client;
    Account account;
    String userId;
    String userDisplayName;
    Handler mainHandler;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        navController = Navigation.findNavController(view);
        RecyclerView postsRecyclerView = view.findViewById(R.id.postsRecyclerView);
        adapter = new PostsAdapter();
        postsRecyclerView.setAdapter(adapter);
        super.onViewCreated(view, savedInstanceState);

        NavigationView navigationView = view.getRootView().findViewById(R.id.nav_view);
        View header = navigationView.getHeaderView(0);
        photoImageView = header.findViewById(R.id.imageView);
        displayNameTextView = header.findViewById(R.id.displayNameTextView);
        emailTextView = header.findViewById(R.id.emailTextView);

        client = new Client(requireContext()).setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);
        mainHandler = new Handler(Looper.getMainLooper());
        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);

        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                mainHandler.post(() -> {
                    userId = result.getId();
                    userDisplayName = result.getName().toString();
                    displayNameTextView.setText(userDisplayName);
                    emailTextView.setText(result.getEmail().toString());
                    cargarFotoPerfil(userId);
                    obtenerPosts();
                });
            }));
        } catch (AppwriteException e) {
            e.printStackTrace();
        }

        view.findViewById(R.id.gotoNewPostFragmentButton).setOnClickListener(v ->
                navController.navigate(R.id.newPostFragment)
        );
    }

    void cargarFotoPerfil(String uid) {
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
                                    Glide.with(requireView()).load(urlFoto).into(photoImageView);
                                } else {
                                    Glide.with(requireView()).load(R.drawable.user).into(photoImageView);
                                }
                            });
                        } else {
                            mainHandler.post(() -> Glide.with(requireView()).load(R.drawable.user).into(photoImageView));
                        }
                    })
            );
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }

    void obtenerPosts() {
        Databases databases = new Databases(client);
        ArrayList<String> queries = new ArrayList<>();
        queries.add(Query.Companion.orderDesc("$createdAt"));
        try {
            databases.listDocuments(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                    queries,
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            Snackbar.make(requireView(), "Error al obtener los posts: " + error.toString(), Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        mainHandler.post(() -> adapter.establecerLista(result));
                    })
            );
        } catch (AppwriteException e) {
            e.printStackTrace();
        }
    }

    private void crearDocumentoRepost(Map<String, Object> postOriginal) throws AppwriteException {
        Map<String, Object> repostData = new HashMap<>();
        repostData.put("isRepost", true);
        repostData.put("originalPostId", postOriginal.get("$id").toString());
        repostData.put("repostedBy", userDisplayName);
        repostData.put("author", postOriginal.get("author"));
        repostData.put("authorPhotoUrl", postOriginal.get("authorPhotoUrl"));
        repostData.put("content", postOriginal.get("content"));
        repostData.put("mediaUrl", postOriginal.get("mediaUrl"));
        repostData.put("mediaType", postOriginal.get("mediaType"));
        repostData.put("uid", userId);
        repostData.put("timestamp", System.currentTimeMillis());

        Databases databases = new Databases(client);
        databases.createDocument(
                getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                "unique()",
                repostData,
                new ArrayList<>(),
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        Snackbar.make(requireView(), "Error al crear repost: " + error.getMessage(), Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    mainHandler.post(() -> obtenerPosts());
                })
        );
    }

    private void quitarRepost(Map<String, Object> postOriginal) throws AppwriteException {
        List<String> reposts = postOriginal.containsKey("reposts") ? (List<String>) postOriginal.get("reposts") : new ArrayList<>();
        reposts.remove(userId);
        Map<String, Object> dataUpdate = new HashMap<>();
        dataUpdate.put("reposts", reposts);
        Databases databases = new Databases(client);
        databases.updateDocument(
                getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                postOriginal.get("$id").toString(),
                dataUpdate,
                new ArrayList<>(),
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        Snackbar.make(requireView(), "Error al quitar repost", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    try {
                        buscarDocumentoRepost(postOriginal.get("$id").toString(), userDisplayName, databases);
                    } catch (AppwriteException e) {
                        throw new RuntimeException(e);
                    }
                })
        );
    }

    private void buscarDocumentoRepost(String postOriginalId, String reposterName, Databases databases) throws AppwriteException {
        ArrayList<String> queries = new ArrayList<>();
        queries.add(Query.Companion.equal("originalPostId", postOriginalId));
        queries.add(Query.Companion.equal("repostedBy", reposterName));
        databases.listDocuments(
                getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                queries,
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        Snackbar.make(requireView(), "Error al buscar repost", Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    if (result.getDocuments().size() > 0) {
                        String repostDocId = result.getDocuments().get(0).getData().get("$id").toString();
                        databases.deleteDocument(
                                getString(R.string.APPWRITE_DATABASE_ID),
                                getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                                repostDocId,
                                new CoroutineCallback<>((result2, error2) -> {
                                    if (error2 != null) {
                                        Snackbar.make(requireView(), "Error al eliminar repost", Snackbar.LENGTH_LONG).show();
                                        return;
                                    }
                                    mainHandler.post(() -> obtenerPosts());
                                })
                        );
                    }
                })
        );
    }

    class PostViewHolder extends RecyclerView.ViewHolder {
        ImageView authorPhotoImageView, likeImageView, mediaImageView, repostButton;
        TextView authorTextView, contentTextView, numLikesTextView, repostBannerTextView, numRepostsTextView;
        ImageButton deleteButton;

        PostViewHolder(@NonNull View itemView) {
            super(itemView);
            authorPhotoImageView = itemView.findViewById(R.id.authorPhotoImageView);
            likeImageView = itemView.findViewById(R.id.likeImageView);
            mediaImageView = itemView.findViewById(R.id.mediaImage);
            authorTextView = itemView.findViewById(R.id.authorTextView);
            contentTextView = itemView.findViewById(R.id.contentTextView);
            numLikesTextView = itemView.findViewById(R.id.numLikesTextView);
            deleteButton = itemView.findViewById(R.id.deleteButton);
            repostButton = itemView.findViewById(R.id.repostButton);
            repostBannerTextView = itemView.findViewById(R.id.repostBannerTextView);
            numRepostsTextView = itemView.findViewById(R.id.numRepostsTextView);
        }
    }

    class PostsAdapter extends RecyclerView.Adapter<PostViewHolder> {
        DocumentList<Map<String, Object>> lista = null;

        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new PostViewHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.viewholder_post, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            Map<String, Object> post = lista.getDocuments().get(position).getData();

            // Si es un repost (copia estática)
            if (post.containsKey("isRepost") && Boolean.TRUE.equals(post.get("isRepost"))) {
                holder.repostBannerTextView.setVisibility(View.VISIBLE);
                holder.repostBannerTextView.setText("El usuario " + post.get("repostedBy") + " ha reposteado esto");
                holder.authorTextView.setText(post.get("author").toString());
                holder.contentTextView.setText(post.get("content").toString());
                if (post.get("authorPhotoUrl") == null) {
                    holder.authorPhotoImageView.setImageResource(R.drawable.user);
                } else {
                    Glide.with(getContext()).load(post.get("authorPhotoUrl").toString()).circleCrop().into(holder.authorPhotoImageView);
                }
                holder.likeImageView.setVisibility(View.GONE);
                holder.numLikesTextView.setVisibility(View.GONE);
                holder.repostButton.setVisibility(View.GONE);
                holder.numRepostsTextView.setVisibility(View.GONE);
            } else {
                holder.repostBannerTextView.setVisibility(View.GONE);
                holder.likeImageView.setVisibility(View.VISIBLE);
                holder.numLikesTextView.setVisibility(View.VISIBLE);
                holder.repostButton.setVisibility(View.VISIBLE);
                holder.numRepostsTextView.setVisibility(View.VISIBLE);

                if (post.get("authorPhotoUrl") == null) {
                    holder.authorPhotoImageView.setImageResource(R.drawable.user);
                } else {
                    Glide.with(getContext()).load(post.get("authorPhotoUrl").toString()).circleCrop().into(holder.authorPhotoImageView);
                }
                holder.authorTextView.setText(post.get("author").toString());
                holder.contentTextView.setText(post.get("content").toString());

                // Gestión de likes
                List<String> likes = (List<String>) post.get("likes");
                if (likes.contains(userId))
                    holder.likeImageView.setImageResource(R.drawable.like_on);
                else
                    holder.likeImageView.setImageResource(R.drawable.like_off);
                holder.numLikesTextView.setText(String.valueOf(likes.size()));
                holder.likeImageView.setOnClickListener(view -> {
                    Databases databases = new Databases(client);
                    Handler localHandler = new Handler(Looper.getMainLooper());
                    List<String> nuevosLikes = new ArrayList<>(likes);
                    if (nuevosLikes.contains(userId))
                        nuevosLikes.remove(userId);
                    else
                        nuevosLikes.add(userId);
                    Map<String, Object> data = new HashMap<>();
                    data.put("likes", nuevosLikes);
                    try {
                        databases.updateDocument(
                                getString(R.string.APPWRITE_DATABASE_ID),
                                getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                                post.get("$id").toString(),
                                data,
                                new ArrayList<>(),
                                new CoroutineCallback<>((result, error) -> {
                                    if (error != null) {
                                        error.printStackTrace();
                                        return;
                                    }
                                    localHandler.post(() -> obtenerPosts());
                                })
                        );
                    } catch (AppwriteException e) {
                        throw new RuntimeException(e);
                    }
                });

                List<String> reposts = post.containsKey("reposts") ? (List<String>) post.get("reposts") : new ArrayList<>();
                holder.numRepostsTextView.setText(String.valueOf(reposts.size()));
                if (reposts.contains(userId)) {
                    holder.repostButton.setImageResource(R.drawable.repost_on);
                } else {
                    holder.repostButton.setImageResource(R.drawable.repost_off);
                }

                holder.repostButton.setOnClickListener(view -> {
                    Databases databases = new Databases(client);
                    if (!reposts.contains(userId)) {
                        reposts.add(userId);
                        holder.numRepostsTextView.setText(String.valueOf(reposts.size()));
                        holder.repostButton.setImageResource(R.drawable.repost_on);

                        Map<String, Object> dataUpdate = new HashMap<>();
                        dataUpdate.put("reposts", reposts);
                        try {
                            databases.updateDocument(
                                    getString(R.string.APPWRITE_DATABASE_ID),
                                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                                    post.get("$id").toString(),
                                    dataUpdate,
                                    new ArrayList<>(),
                                    new CoroutineCallback<>((result, error) -> {
                                        if (error != null) {
                                            Snackbar.make(requireView(), "Error al actualizar repost", Snackbar.LENGTH_LONG).show();
                                            return;
                                        }
                                        try {
                                            crearDocumentoRepost(post);
                                        } catch (AppwriteException e) {
                                            throw new RuntimeException(e);
                                        }
                                    })
                            );
                        } catch (AppwriteException e) {
                            throw new RuntimeException(e);
                        }
                    } else {
                        reposts.remove(userId);
                        holder.numRepostsTextView.setText(String.valueOf(reposts.size()));
                        holder.repostButton.setImageResource(R.drawable.repost_off);
                        try {
                            quitarRepost(post);
                        } catch (AppwriteException e) {
                            throw new RuntimeException(e);
                        }
                    }
                });
            }

            // Miniatura de media
            if (post.get("mediaUrl") != null) {
                holder.mediaImageView.setVisibility(View.VISIBLE);
                if ("audio".equals(post.get("mediaType").toString())) {
                    Glide.with(requireView()).load(R.drawable.audio).centerCrop().into(holder.mediaImageView);
                } else {
                    Glide.with(requireView()).load(post.get("mediaUrl").toString()).centerCrop().into(holder.mediaImageView);
                }
                holder.mediaImageView.setOnClickListener(view -> {
                    appViewModel.postSeleccionado.setValue(post);
                    navController.navigate(R.id.mediaFragment);
                });
            } else {
                holder.mediaImageView.setVisibility(View.GONE);
            }

            // Botón borrar (se oculta en reposts)
            if (post.containsKey("isRepost") && Boolean.TRUE.equals(post.get("isRepost"))) {
                holder.deleteButton.setVisibility(View.GONE);
            } else {
                String postUid = post.get("uid").toString();
                if (postUid.equals(userId)) {
                    holder.deleteButton.setVisibility(View.VISIBLE);
                    holder.deleteButton.setOnClickListener(view -> {
                        Databases databases = new Databases(client);
                        Handler localHandler = new Handler(Looper.getMainLooper());
                        databases.deleteDocument(
                                getString(R.string.APPWRITE_DATABASE_ID),
                                getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                                post.get("$id").toString(),
                                new CoroutineCallback<>((result, error) -> {
                                    if (error != null) {
                                        Snackbar.make(getView(), "Error al borrar: " + error.toString(), Snackbar.LENGTH_LONG).show();
                                        return;
                                    }
                                    localHandler.post(() -> obtenerPosts());
                                })
                        );
                    });
                } else {
                    holder.deleteButton.setVisibility(View.GONE);
                }
            }
        }

        @Override
        public int getItemCount() {
            return lista == null ? 0 : lista.getDocuments().size();
        }

        public void establecerLista(DocumentList<Map<String, Object>> lista) {
            this.lista = lista;
            notifyDataSetChanged();
        }
    }
}
