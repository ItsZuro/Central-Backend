package com.centrral.centralres.features.products.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.centrral.centralres.features.products.dto.category.request.CategoryRequest;
import com.centrral.centralres.features.products.dto.category.response.CategoryResponse;
import com.centrral.centralres.features.products.exceptions.CategoryNotFoundException;
import com.centrral.centralres.features.products.model.Category;
import com.centrral.centralres.features.products.repository.CategoryRepository;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Test
    void shouldReturnAllCategories() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                Category.builder().id(1L).name("Bebidas").build(),
                Category.builder().id(2L).name("Postres").build()));

        List<CategoryResponse> result = categoryService.getAll();

        assertThat(result)
                .extracting(CategoryResponse::getName)
                .containsExactly("Bebidas", "Postres");
    }

    @Test
    void shouldReturnCategoryById() {
        when(categoryRepository.findById(1L))
                .thenReturn(Optional.of(Category.builder().id(1L).name("Bebidas").build()));

        CategoryResponse result = categoryService.getById(1L);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getName()).isEqualTo("Bebidas");
    }

    @Test
    void shouldFailWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> categoryService.getById(99L))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void shouldCreateCategory() {
        CategoryRequest request = CategoryRequest.builder().name("Entradas").build();
        when(categoryRepository.save(any(Category.class)))
                .thenReturn(Category.builder().id(3L).name("Entradas").build());

        CategoryResponse result = categoryService.create(request);

        assertThat(result.getId()).isEqualTo(3L);
        assertThat(result.getName()).isEqualTo("Entradas");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void shouldUpdateCategory() {
        Category existing = Category.builder().id(1L).name("Bebida").build();
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(existing)).thenReturn(existing);

        CategoryResponse result = categoryService.update(
                1L,
                CategoryRequest.builder().name("Bebidas").build());

        assertThat(result.getName()).isEqualTo("Bebidas");
        verify(categoryRepository).save(existing);
    }

    @Test
    void shouldFailWhenDeletingUnknownCategory() {
        when(categoryRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> categoryService.delete(99L))
                .isInstanceOf(CategoryNotFoundException.class)
                .hasMessageContaining("99");
    }
}
